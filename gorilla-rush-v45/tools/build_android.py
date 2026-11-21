#!/usr/bin/env python3
"""Compile the Android WebView SCREEN edition + external VR browser launcher.
This is NOT a native PICO VR port. Tools reused from the audited repository pipeline."""
from pathlib import Path
import os, sys, subprocess, zipfile, hashlib, secrets, shutil, json
ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parent
CACHE=REPO/'.cache/android'
TOOLS=Path(os.environ.get('ANDROID_TOOLS_DIR', CACHE/'minapk/package/tools'))
AAPT=Path(os.environ.get('AAPT2', CACHE/'aaptjs/package/bin/x64/linux/aapt2'))
BUILD=ROOT/'build/android'
OUT=ROOT/'downloads'

def run(*args):
    subprocess.run([str(a) for a in args],check=True,cwd=ROOT)

def main():
    java_home=os.environ.get('JAVA_HOME')
    if not java_home:
        import jdk4py
        java_home=str(jdk4py.JAVA_HOME)
    java=Path(java_home)/'bin/java'
    hashes={
        'android.jar':'6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad',
        'd8.jar':'d43c8a94c9b1f1da1a7cc49c32b81e8cee1708b37ee8b530a81a0688222b42c0',
        'apksigner.jar':'eefdd6aed9db9fb849e4c98a50d8741e19d1b674ba6547220bcb9c3ed152123a',
        'ecj-3.45.0.jar':'c8f5e66787ea6e4e0394ba3051c86a4edc50683ce9850f71739646529a99a2d7'
    }
    for name,digest in hashes.items():
        if hashlib.sha256((TOOLS/name).read_bytes()).hexdigest()!=digest:
            raise RuntimeError('Unexpected build tool hash: '+name)
    if hashlib.sha256(AAPT.read_bytes()).hexdigest()!='e4dff6060827a3e401bce376e47916f2f93a89602af54973ffd3fdfc2528be3f':
        raise RuntimeError('Unexpected Linux AAPT2 hash')
    if BUILD.exists(): shutil.rmtree(BUILD)
    (BUILD/'classes').mkdir(parents=True);(BUILD/'dex').mkdir();OUT.mkdir(parents=True,exist_ok=True)
    sources=sorted((ROOT/'android/src').rglob('*.java'))
    run(java,'-jar',TOOLS/'ecj-3.45.0.jar','-8','-encoding','UTF-8','-warn:none','-classpath',TOOLS/'android.jar','-d',BUILD/'classes',*sources)
    with zipfile.ZipFile(BUILD/'classes.jar','w',zipfile.ZIP_DEFLATED) as jar:
        for file in (BUILD/'classes').rglob('*.class'): jar.write(file,file.relative_to(BUILD/'classes'))
    run(java,'-cp',TOOLS/'d8.jar','com.android.tools.r8.D8','--lib',TOOLS/'android.jar','--min-api','23','--release','--output',BUILD/'dex',BUILD/'classes.jar')
    run(AAPT,'compile','--dir',ROOT/'android/res','-o',BUILD/'resources.zip')
    run(AAPT,'link','-o',BUILD/'unsigned.apk','-I',TOOLS/'android.jar','--manifest',ROOT/'android/AndroidManifest.xml','--min-sdk-version','23','--target-sdk-version','28','--version-code','1','--version-name','1.0.0',BUILD/'resources.zip')
    with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
        for dex in (BUILD/'dex').glob('*.dex'): apk.write(dex,dex.name)
        for folder in ('assets','js','css','lib'):
            for asset in sorted((ROOT/folder).rglob('*')):
                if asset.is_file(): apk.write(asset,'assets/game/'+str(asset.relative_to(ROOT)))
        apk.write(ROOT/'index.html','assets/game/index.html')
        apk.write(ROOT/'CREDITS.md','assets/game/CREDITS.md')
        apk.write(ROOT/'docs/INPUT_PROVENANCE.json','assets/game/docs/INPUT_PROVENANCE.json')
    key=Path(os.environ.get('SIGNING_KEYSTORE',CACHE/'gorillav45-development.p12'))
    password=Path(os.environ.get('SIGNING_PASSWORD_FILE',CACHE/'gorillav45-signing-pass'))
    alias=os.environ.get('SIGNING_ALIAS','development')
    if not key.exists():
        if 'SIGNING_KEYSTORE' in os.environ: raise RuntimeError('Configured keystore does not exist')
        password.write_text(secrets.token_urlsafe(32));password.chmod(0o600)
        run(Path(java_home)/'bin/keytool','-genkeypair','-alias',alias,'-keyalg','RSA','-keysize','2048','-validity','3650','-storetype','PKCS12','-keystore',key,'-storepass:file',password,'-keypass:file',password,'-dname','CN=Gorilla Rush v45 Screen Edition Development, OU=Unvalidated Device Build, O=Independent Project, C=BR')
        key.chmod(0o600)
    target=OUT/'Gorilla-Rush-v45-PicoG2.apk'
    run(java,'-jar',TOOLS/'apksigner.jar','sign','--ks',key,'--ks-key-alias',alias,'--ks-pass','file:'+str(password),'--min-sdk-version','23','--v1-signing-enabled','true','--v2-signing-enabled','true','--v4-signing-enabled','false','--out',target,BUILD/'unsigned.apk')
    validation=subprocess.check_output([str(java),'-jar',str(TOOLS/'apksigner.jar'),'verify','--verbose','--print-certs',str(target)],text=True)
    badging=subprocess.check_output([str(AAPT),'dump','badging',str(target)],text=True)
    assert "package: name='com.picog2.gorillarushv45'" in badging and "versionName='1.0.0'" in badging
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
        assert archive.getinfo('classes.dex').file_size>5000
        assert archive.getinfo('assets/game/assets/58aacc462c7f9edb223f.bin').file_size>1000000
        assert archive.getinfo('assets/game/assets/49b02532368b75c9838f.glb').file_size>10000
        for folder in ('assets','js','css','lib'):
            for asset in (ROOT/folder).rglob('*'):
                if asset.is_file(): assert archive.read('assets/game/'+str(asset.relative_to(ROOT))) == asset.read_bytes()
        assert not any(name.startswith(('lib/','assets/bin/Data/')) for name in archive.namelist())
    sha=hashlib.sha256(target.read_bytes()).hexdigest()
    (OUT/'Gorilla-Rush-v45-PicoG2.apk.sha256').write_text(sha+'  Gorilla-Rush-v45-PicoG2.apk\n')
    (ROOT/'docs/ANDROID_BUILD.txt').write_text('REAL JAVA/DEX/APK BUILD. STATIC VALIDATION ONLY.\nNo headset, emulator, installation, PICO stereo or controller-pose test was performed.\n\n'+validation+'\n'+badging+'\nSHA-256: '+sha+'\nSize: '+str(target.stat().st_size)+' bytes\n')
    print('Compiled and signed:',target,'bytes:',target.stat().st_size)
    print('Not device-tested. No proprietary PICO runtime or controller pose integration.')
if __name__=='__main__':main()
