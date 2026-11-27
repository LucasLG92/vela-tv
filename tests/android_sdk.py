"""Use the compile platform of the APK under test, including standalone legacy Lite."""


def compile_jar(sdk, legacy_lite=False):
    api = 34 if legacy_lite else 36
    jar = sdk / 'platforms' / f'android-{api}' / 'android.jar'
    if not jar.is_file():
        raise FileNotFoundError(f'Install the Android SDK package "platforms;android-{api}"')
    return jar
