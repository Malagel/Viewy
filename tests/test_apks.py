"""Opt-in host-only checks of assembled APKs; never invokes ADB or Gradle.

Build both APKs with -Pviewy.localReleaseSigning=true, then run with
VIEWY_VERIFY_APKS=1 and ANDROID_HOME/JAVA_HOME set (see docs/VALIDATION.md).
"""
import os
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(os.environ.get('VIEWY_VERIFY_APKS') == '1',
                     'requires assembled APKs and explicit VIEWY_VERIFY_APKS=1')
class ApkTest(unittest.TestCase):
    def test_variant_identity_debuggability_and_development_signing(self):
        tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
        certificates = []
        for variant, app_id, label in (
                ('debug', 'com.malagel.viewy.debug', 'ViewyDebug'),
                ('release', 'com.malagel.viewy', 'Viewy')):
            with self.subTest(variant=variant):
                apk = ROOT / f'app/build/outputs/apk/{variant}/app-{variant}.apk'
                self.assertTrue(apk.is_file(), f'Build the {variant} APK first')
                badging = subprocess.check_output(
                    [str(tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
                self.assertIn(f"package: name='{app_id}'", badging)
                self.assertIn(f"application-label:'{label}'", badging)
                self.assertIn("launchable-activity: name='com.malagel.viewy.MainActivity'", badging)
                self.assertEqual('application-debuggable' in badging, variant == 'debug')
                labels = re.findall(r"^application-label(?:-[^:]+)?:'([^']*)'", badging, re.M)
                self.assertTrue(labels)
                self.assertEqual(set(labels), {label})
                signing = subprocess.check_output(
                    [str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
                self.assertIn('CN=Android Debug', signing)
                certificates.append(re.search(
                    r'Signer #1 certificate SHA-256 digest: (\S+)', signing).group(1))
        self.assertEqual(len(certificates), 2)
        self.assertEqual(certificates[0], certificates[1])

    def test_release_r8_outputs(self):
        mapping = ROOT / 'app/build/outputs/mapping/release'
        for name in ('mapping.txt', 'configuration.txt', 'usage.txt', 'resources.txt'):
            artifact = mapping / name
            self.assertTrue(artifact.is_file(), f'Missing R8 output: {artifact}')
            self.assertGreater(artifact.stat().st_size, 0)
        config = (mapping / 'configuration.txt').read_text()
        self.assertIn('proguard-android-optimize.txt', config)
        self.assertIn('is not reachable.', (mapping / 'resources.txt').read_text())
        for disabled in ('-dontoptimize', '-dontshrink', '-dontobfuscate'):
            self.assertNotRegex(config, rf'(?m)^\s*{disabled}\s*$')
        self.assertRegex((mapping / 'mapping.txt').read_text(),
                         r'(?m)^com\.malagel\.viewy\..+ -> (?!com\.malagel\.viewy\.).+:$')


if __name__ == '__main__':
    unittest.main()
