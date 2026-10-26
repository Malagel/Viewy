"""Isolated deployment checks; no real ADB device, SDK, or Gradle invocation.

Run with: python -m unittest discover -s tests -p 'test_deploy.py'
"""
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/deploy.sh'
SERVICE = 'adb-R5CTA21K2WF-test._adb-tls-connect._tcp'


class DeployTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='viewy-deploy-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / 'repo with spaces'
        (self.repo / 'scripts').mkdir(parents=True)
        # Shorten only the polling budget so failure cases run quickly.
        script = SCRIPT.read_text().replace('deadline=$((SECONDS + 15))',
                                            'deadline=$((SECONDS + 2))')
        self.write_executable(self.repo / 'scripts/deploy.sh', script)
        sdk = self.root / 'sdk'
        (sdk / 'platform-tools').mkdir(parents=True)
        jdk = self.root / 'jdk'
        (jdk / 'bin').mkdir(parents=True)
        for name in ('java', 'javac'):
            (jdk / 'bin' / name).symlink_to('/usr/bin/true')
        self.log = self.root / 'calls'
        self.env = dict(os.environ, JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk),
                        ANDROID_SDK_ROOT=str(sdk), ANDROID_SERIAL='',
                        ANDROID_USER_HOME=str(self.root / 'android'),
                        GRADLE_USER_HOME=str(self.root / 'gradle'),
                        TEST_LOG=str(self.log), TEST_ROOT=str(self.root),
                        SERVICE=SERVICE, SCENARIO='service')
        self.write_executable(sdk / 'platform-tools/adb', '''#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$TEST_LOG"
case "$1" in
    devices)
        printf 'List of devices attached\\n'
        case "$SCENARIO" in
            service|build-fail|install-fail|launch-fail|missing-apk) printf '%s device\\n' "$SERVICE" ;;
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
                printf 'adb-R5CTA21K2WF-test _adb-tls-connect._tcp. 192.0.2.1:34567\\n'
                printf 'adb-R5CTA21K2WF-test _adb-tls-pairing._tcp 192.0.2.1:45678\\n'
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
                        printf 'R5CTA21K2WF\\r\\n'
                    else
                        printf 'OTHER\\n'
                    fi
                elif [[ "$SCENARIO" == launch-fail ]]; then
                    printf 'Error: Activity class does not exist.\\n'
                else
                    printf 'Status: ok\\n'
                fi ;;
            install) [[ "$SCENARIO" != install-fail ]] || exit 1 ;;
        esac ;;
esac
''')
        self.write_executable(self.repo / 'gradlew', '''#!/usr/bin/env bash
printf 'gradle %s\\n' "$*" >> "$TEST_LOG"
[[ "$SCENARIO" != build-fail ]] || exit 1
if [[ "$SCENARIO" != missing-apk ]]; then
    mkdir -p app/build/outputs/apk/debug
    touch app/build/outputs/apk/debug/app-debug.apk
fi
''')

    @staticmethod
    def write_executable(path, content):
        path.write_text(content)
        path.chmod(0o755)

    def run_script(self, scenario, serial='', success=True):
        self.env.update(SCENARIO=scenario, ANDROID_SERIAL=serial)
        result = subprocess.run([str(self.repo / 'scripts/deploy.sh')],
                                cwd='/tmp', env=self.env, capture_output=True,
                                text=True, timeout=8)
        self.assertEqual(result.returncode == 0, success,
                         result.stdout + result.stderr)
        calls = self.log.read_text().splitlines()
        if success:
            target = serial or ('192.0.2.1:34567' if scenario in ('ip', 'offline-alias') else SERVICE)
            install = f'-s {target} install -r {self.repo}/app/build/outputs/apk/debug/app-debug.apk'
            launch = f'-s {target} shell am start -W -n com.malagel.viewy/.MainActivity'
            self.assertIn(install, calls)
            self.assertIn(launch, calls)
            self.assertLess(calls.index('gradle :app:assembleDebug'), calls.index(install))
            self.assertLess(calls.index(install), calls.index(launch))
        return calls

    def assert_no_build(self, scenario, serial=''):
        calls = self.run_script(scenario, serial, success=False)
        self.assertFalse(any(c.startswith('gradle') or ' install ' in c for c in calls))

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

    def test_android_launch_error_is_failure(self):
        self.run_script('launch-fail', success=False)


if __name__ == '__main__':
    unittest.main()
