"""Isolated deployment checks; no real ADB device, SDK, or Gradle invocation.

Run with: python -m unittest discover -s tests -p 'test_deploy.py'
"""
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import time
import unittest


SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
CONFIG_KEYS = ('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'ANDROID_SERIAL',
               'ANDROID_USER_HOME', 'GRADLE_USER_HOME', 'PHONE_SERIAL')
SERVICE = 'adb-FICTIONAL123-test._adb-tls-connect._tcp'


class DeployTest(unittest.TestCase):
    variant = 'debug'
    label = 'ViewyDebug'
    app_id = 'com.malagel.viewy.debug'
    task = ':app:assembleDebug'
    options = ''

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='viewy-deploy-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo with spaces'
        (self.repo / 'scripts').mkdir(parents=True)
        # Shorten only the polling budget so failure cases run quickly.
        for name in ('deploy.sh', 'deploy-release.sh', 'deploy-common.sh'):
            script = (SCRIPTS / name).read_text().replace(
                'deadline=$((SECONDS + 15))', 'deadline=$((SECONDS + 2))')
            self.write_executable(self.repo / 'scripts' / name, script)
        sdk = self.root / 'sdk'
        (sdk / 'platform-tools').mkdir(parents=True)
        jdk = self.root / 'jdk'
        (jdk / 'bin').mkdir(parents=True)
        for name in ('java', 'javac'):
            (jdk / 'bin' / name).symlink_to('/usr/bin/true')
        self.log = self.root / 'calls'
        base_env = {k: v for k, v in os.environ.items() if k not in CONFIG_KEYS}
        self.env = dict(base_env, JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk),
                        ANDROID_SDK_ROOT=str(sdk), ANDROID_SERIAL='',
                        ANDROID_USER_HOME=str(self.root / 'android'),
                        GRADLE_USER_HOME=str(self.root / 'gradle'),
                        TEST_LOG=str(self.log), TEST_ROOT=str(self.root),
                        SERVICE=SERVICE, SCENARIO='service')
        self.config = self.repo / 'scripts/deploy.local.conf'
        self.config.write_text("PHONE_SERIAL='FICTIONAL123'\n")
        self.write_executable(sdk / 'platform-tools/adb', '''#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$TEST_LOG"
case "$1" in
    devices)
        printf 'List of devices attached\\n'
        case "$SCENARIO" in
            service|build-fail|install-fail|launch-fail|missing-apk|signing-fail) printf '%s device\\n' "$SERVICE" ;;
            duplicates) printf '192.0.2.1:34567 device\\n%s device\\nother device\\n' "$SERVICE" ;;
            offline-alias) printf '%s offline\\n192.0.2.1:34567 device\\n' "$SERVICE" ;;
            ip) printf '192.0.2.1:34567 device\\nother device\\n' ;;
            unknown) printf 'other device\\n' ;;
            offline) printf '%s offline\\n' "$SERVICE" ;;
            unauthorized) printf '%s unauthorized\\n' "$SERVICE" ;;
            reconnect|delayed)
                if [[ -f "$TEST_ROOT/ready" ]]; then
                    printf '%s device\\n' "$SERVICE"
                fi ;;
        esac ;;
    mdns)
        case "$SCENARIO" in
            reconnect)
                printf 'List of discovered mdns services\\n'
                printf 'adb-FICTIONAL123-test _adb-tls-connect._tcp. 192.0.2.1:34567\\n'
                printf 'adb-FICTIONAL123-test _adb-tls-pairing._tcp 192.0.2.1:45678\\n'
                printf 'adb-OTHER-test _adb-tls-connect._tcp 192.0.2.2:34567\\n' ;;
            delayed)
                if [[ -f "$TEST_ROOT/polled" ]]; then
                    touch "$TEST_ROOT/ready"
                fi
                touch "$TEST_ROOT/polled" ;;
            hang) sleep 10 ;;
        esac ;;
    connect) touch "$TEST_ROOT/ready" ;;
    -s)
        case "$3" in
            get-state) printf 'device\\n' ;;
            shell)
                if [[ "$4" == -n && "$5" == getprop ]]; then
                    if [[ "$2" == 192.0.2.1:34567 ]]; then
                        printf 'FICTIONAL123\\r\\n'
                    else
                        printf 'OTHER\\n'
                    fi
                elif [[ "$SCENARIO" == launch-fail ]]; then
                    printf 'Error: Activity class does not exist.\\n'
                else
                    printf 'Status: ok\\n'
                fi ;;
            install)
                if [[ "$SCENARIO" == signing-fail ]]; then
                    printf 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]\\n'
                    exit 1
                fi
                [[ "$SCENARIO" != install-fail ]] || exit 1 ;;
        esac ;;
esac
''')
        self.write_executable(self.repo / 'gradlew', '''#!/usr/bin/env bash
printf '%s\\n' "JAVA_HOME=$JAVA_HOME" "ANDROID_HOME=$ANDROID_HOME" "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-}" "ANDROID_USER_HOME=$ANDROID_USER_HOME" "GRADLE_USER_HOME=$GRADLE_USER_HOME" > "$TEST_ROOT/build-env"
printf 'gradle %s\\n' "$*" >> "$TEST_LOG"
[[ "$SCENARIO" != build-fail ]] || exit 1
if [[ "$SCENARIO" != missing-apk ]]; then
    case "$*" in
        *:app:assembleDebug) variant=debug ;;
        *:app:assembleRelease) variant=release ;;
        *) exit 1 ;;
    esac
    mkdir -p "app/build/outputs/apk/$variant"
    touch "app/build/outputs/apk/$variant/app-$variant.apk"
fi
''')

    @staticmethod
    def write_executable(path, content):
        path.write_text(content)
        path.chmod(0o755)

    def run_script(self, scenario, serial='', success=True):
        self.env.update(SCENARIO=scenario, ANDROID_SERIAL=serial)
        entry = 'deploy.sh' if self.variant == 'debug' else 'deploy-release.sh'
        result = subprocess.run([str(self.repo / 'scripts' / entry)],
                                cwd='/tmp', env=self.env, capture_output=True,
                                text=True, timeout=8)
        self.assertEqual(result.returncode == 0, success,
                         result.stdout + result.stderr)
        self.result = result
        calls = self.log.read_text().splitlines() if self.log.exists() else []
        if success:
            target = serial or ('192.0.2.1:34567' if scenario in ('ip', 'offline-alias') else SERVICE)
            install = f'-s {target} install -r {self.repo}/app/build/outputs/apk/{self.variant}/app-{self.variant}.apk'
            launch = f'-s {target} shell am start -W -n {self.app_id}/com.malagel.viewy.MainActivity'
            self.assertIn(install, calls)
            self.assertIn(launch, calls)
            self.assertLess(calls.index(f'gradle --no-daemon --max-workers=1 --no-watch-fs {self.options}{self.task}'), calls.index(install))
            self.assertLess(calls.index(install), calls.index(launch))
        self.assertFalse(any('uninstall' in c or 'pm clear' in c for c in calls))
        if success:
            self.assertIn(f'Success: {self.label} built, installed, and launched', result.stdout)
        return calls

    def assert_no_build(self, scenario, serial=''):
        calls = self.run_script(scenario, serial, success=False)
        self.assertFalse(any(c.startswith('gradle') or ' install ' in c for c in calls))

    def test_local_configuration(self):
        # Tool paths and phone identity come from the isolated configuration.
        settings = {key: self.env.pop(key) for key in CONFIG_KEYS if key in self.env}
        settings['PHONE_SERIAL'] = 'FICTIONAL123'
        self.config.write_text(''.join(f'{key}={shlex.quote(value)}\n'
                                       for key, value in settings.items()))
        self.run_script('service')
        build_env = (self.root / 'build-env').read_text().splitlines()
        for key, value in settings.items():
            if key not in ('PHONE_SERIAL', 'ANDROID_SERIAL'):
                self.assertIn(f'{key}={value}', build_env)

    def test_environment_overrides_local_configuration(self):
        self.config.write_text("""JAVA_HOME=/missing/jdk
ANDROID_HOME=/missing/sdk
ANDROID_SDK_ROOT=/missing/other-sdk
ANDROID_USER_HOME=/missing/android
GRADLE_USER_HOME=/missing/gradle
PHONE_SERIAL=WRONG
ANDROID_SERIAL=missing
""")
        self.env['PHONE_SERIAL'] = 'FICTIONAL123'
        self.run_script('service')
        build_env = (self.root / 'build-env').read_text().splitlines()
        for key in ('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_USER_HOME', 'GRADLE_USER_HOME'):
            self.assertIn(f'{key}={self.env[key]}', build_env)

    def test_explicit_sdk_root_overrides_local_sdk_home(self):
        self.config.write_text('PHONE_SERIAL=FICTIONAL123\nANDROID_HOME=/missing/sdk\n')
        del self.env['ANDROID_HOME']
        self.run_script('service')

    def test_explicit_sdk_home_overrides_local_sdk_root(self):
        self.config.write_text('PHONE_SERIAL=FICTIONAL123\nANDROID_SDK_ROOT=/missing/sdk\n')
        del self.env['ANDROID_SDK_ROOT']
        self.run_script('service')

    def test_conflicting_explicit_sdk_aliases_are_rejected(self):
        self.env['ANDROID_SDK_ROOT'] = '/missing/sdk'
        self.assert_no_build('service')
        self.assertIn('must refer to the same SDK', self.result.stderr)

    def test_no_local_configuration_with_explicit_target(self):
        self.config.unlink()
        self.run_script('duplicates', serial='other')

    def test_missing_configuration_has_actionable_errors(self):
        self.config.unlink()
        for key, message in (('PHONE_SERIAL', 'Set PHONE_SERIAL'),
                             ('ANDROID_HOME', 'Set ANDROID_HOME'),
                             ('JAVA_HOME', 'Set JAVA_HOME')):
            if key == 'ANDROID_HOME':
                del self.env['ANDROID_HOME']
                del self.env['ANDROID_SDK_ROOT']
            elif key == 'JAVA_HOME':
                del self.env['JAVA_HOME']
            self.assert_no_build('service')
            self.assertIn(message, self.result.stderr)
            self.assertIn('deploy.local.conf', self.result.stderr)

    def test_portable_cache_defaults(self):
        del self.env['ANDROID_USER_HOME']
        del self.env['GRADLE_USER_HOME']
        self.env['HOME'] = str(self.root / 'isolated home')
        self.run_script('service')
        build_env = (self.root / 'build-env').read_text().splitlines()
        self.assertIn(f"ANDROID_USER_HOME={self.env['HOME']}/.android", build_env)
        self.assertIn(f"GRADLE_USER_HOME={self.env['HOME']}/.gradle", build_env)

    def test_service(self):
        self.run_script('service')

    def test_duplicate_transports_prefer_service(self):
        self.run_script('duplicates')

    def test_ip_transport_identified_without_mdns(self):
        self.run_script('ip')

    def test_offline_service_does_not_hide_reachable_ip_alias(self):
        self.run_script('offline-alias')

    def test_reconnect_known_service_only(self):
        calls = self.run_script('reconnect')
        self.assertEqual([c for c in calls if c.startswith('connect ')],
                         ['connect 192.0.2.1:34567'])

    def test_delayed_auto_connect_with_empty_mdns(self):
        self.run_script('delayed')

    def test_explicit_target_overrides_preference(self):
        self.run_script('duplicates', serial='other')

    def test_missing_explicit_target_never_falls_back(self):
        self.assert_no_build('service', serial='missing')

    def test_unidentified_phone_is_rejected(self):
        self.assert_no_build('unknown')

    def test_offline_phone_is_rejected(self):
        self.assert_no_build('offline')

    def test_unauthorized_phone_is_rejected(self):
        self.assert_no_build('unauthorized')

    def test_empty_discovery_and_hanging_adb_are_bounded(self):
        start = time.monotonic()
        self.assert_no_build('hang')
        self.assertLess(time.monotonic() - start, 5)

    def test_build_failure_stops_install(self):
        calls = self.run_script('build-fail', success=False)
        self.assertFalse(any(' install ' in c for c in calls))

    def test_missing_apk_stops_install(self):
        calls = self.run_script('missing-apk', success=False)
        self.assertFalse(any(' install ' in c for c in calls))

    def test_install_failure_stops_launch(self):
        calls = self.run_script('install-fail', success=False)
        self.assertFalse(any('shell am start' in c for c in calls))

    def test_signing_mismatch_stops_launch_and_explains_data_preservation(self):
        calls = self.run_script('signing-fail', success=False)
        self.assertFalse(any('shell am start' in c for c in calls))
        self.assertIn('Signing certificate mismatch', self.result.stderr)
        self.assertIn(self.app_id, self.result.stderr)
        self.assertIn('no data was cleared', self.result.stderr)

    def test_android_launch_error_is_failure(self):
        self.run_script('launch-fail', success=False)


class ReleaseDeployTest(DeployTest):
    variant = 'release'
    label = 'Viewy'
    app_id = 'com.malagel.viewy'
    task = ':app:assembleRelease'
    options = '-Pviewy.localReleaseSigning=true '


if __name__ == '__main__':
    unittest.main()
