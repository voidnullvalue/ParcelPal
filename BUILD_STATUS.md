# Build status

Local source checks can run without the Android SDK:

```bash
bash tools/validate-source.sh
```

The authoritative Android build runs in `.github/workflows/android.yml` on every push and pull request. It installs Android API 36, runs unit tests and lint, assembles the debug APK, and uploads the APK as a workflow artifact.
