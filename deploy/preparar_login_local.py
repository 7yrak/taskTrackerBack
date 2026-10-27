#!/usr/bin/env python3
"""Deja el servidor listo para que el job de Jenkins instale TaskTracker con login local.

    sudo python3 deploy/preparar_login_local.py --check-only   # ensayo: no escribe nada
    sudo python3 deploy/preparar_login_local.py                # aplica

El job `task-tracker-build` ya clona main, compila y llama por ssh a
/usr/local/sbin/deploy-tasktracker-from-jenkins.sh. Ese deployer rechaza cualquier JAR sin
KovaSecurity.class (guard del corte a KOVA) y el servicio no arranca sin usuario y clave. Este script:
  1. Quita ese guard del deployer (las comprobaciones de "API sin sesion = 401" se conservan).
  2. Pide la contrasena (2 veces, minimo 12 caracteres), genera el hash BCrypt y deja usuario y hash en
     /etc/tasktracker-kova.env; elimina SPRING_PROFILES_ACTIVE=kova-personal y conserva la base de datos.
  3. Guarda copia de cada archivo que modifica. Es repetible. No detiene ni despliega nada:
     despues se ejecuta el job en Jenkins.
Con --workspace ademas lleva a origin/main los repos de /home/tyrak/proyectos/gestion-proyectos y parcha
su deploy-from-jenkins-zip.sh (flujo antiguo; el job actual no lo usa).

La contrasena nunca va como argumento, ni se imprime, ni se guarda en Git.
"""
from datetime import datetime,timezone
from pathlib import Path
import argparse,getpass,os,pwd,re,shlex,subprocess,sys
sys.dont_write_bytecode=True

WORKSPACE=Path('/home/tyrak/proyectos/gestion-proyectos')
REPOS=('taskTrackerBack','taskTrackerFront')
ZIP_DEPLOYER='deploy-from-jenkins-zip.sh'
SBIN_DEPLOYER=Path('/usr/local/sbin/deploy-tasktracker-from-jenkins.sh')
ENV_FILE=Path('/etc/tasktracker-kova.env')
BACKUPS=Path('/home/tyrak/.local/share/kova-managed-applications/local-auth')
OWNER='tyrak'
BCRYPT=re.compile(r'\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}')
MARKER='# KOVA-only artifact and private-health guard'
REQUIRED=['BOOT-INF/classes/com/tasktracker/security/KovaSecurity.class','BOOT-INF/classes/application-kova-personal.properties']
CHECK="[[ \"$(curl --silent --output /dev/null --write-out '%{{http_code}}' --max-time {t} http://127.0.0.1:8081/api/projects)\" == 401 ]]"
CURL_EDITS=[('curl --fail --silent --max-time 2 http://127.0.0.1:8081/api/projects >/dev/null',CHECK.format(t=2)),
            ('curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8081/api/projects >/dev/null',CHECK.format(t=5))]

def guard_block():
    """Texto exacto que el corte a KOVA inserto en el deployer de /usr/local/sbin."""
    return ('\n'+MARKER+"\npython3 - \"$JAR_FILE\" <<'KOVA_ARTIFACT'\nimport sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as jar:\n    required="
            +repr(REQUIRED)+"\n    if not set(required).issubset(jar.namelist()):\n        raise SystemExit('KOVA-only artifact required; existing service was not stopped')\nKOVA_ARTIFACT")

def relax_deployer(text):
    if MARKER not in text:return text,False
    block=guard_block()
    if text.count(block)!=1 or text.count(MARKER)!=1:raise ValueError('El guard de '+SBIN_DEPLOYER.name+' no tiene la forma esperada; no se cambio nada')
    return text.replace(block,''),True

def patch_zip_deployer(text):
    if all(new in text for _,new in CURL_EDITS):return text,False
    for old,_ in CURL_EDITS:
        if text.count(old)!=1:raise ValueError(ZIP_DEPLOYER+' no tiene la forma esperada ('+old[:40]+'...); no se cambio nada')
    for old,new in CURL_EDITS:text=text.replace(old,new)
    return text,True

def rewrite_env(text,username,password_hash):
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,64}',username or ''):raise ValueError('Usuario invalido')
    if not BCRYPT.fullmatch(password_hash):raise ValueError('El hash debe ser BCrypt')
    drop={'SPRING_PROFILES_ACTIVE','TASKTRACKER_USER','TASKTRACKER_PASSWORD_HASH'}
    kept=[line for line in text.splitlines() if line.partition('=')[0].strip() not in drop]
    while kept and not kept[-1].strip():kept.pop()
    kept+=['TASKTRACKER_USER='+shlex.quote(username),'TASKTRACKER_PASSWORD_HASH='+shlex.quote(password_hash)]
    return '\n'.join(kept)+'\n'

def hash_password(password):
    try:done=subprocess.run(['htpasswd','-inBC','12','owner'],input=password.encode(),capture_output=True,check=True)
    except (OSError,subprocess.CalledProcessError):raise RuntimeError('Falta htpasswd: sudo apt install apache2-utils') from None
    value=done.stdout.decode().strip().partition(':')[2]
    if not BCRYPT.fullmatch(value):raise RuntimeError('Salida inesperada de htpasswd')
    return value

def owner_exists():
    try:pwd.getpwnam(OWNER);return True
    except KeyError:return False

def git(directory,*args,as_owner=True):
    command=['git','-C',str(directory),*args]
    if as_owner and os.geteuid()==0 and owner_exists():command=['runuser','-u',OWNER,'--']+command
    done=subprocess.run(command,capture_output=True,text=True)
    if done.returncode:raise RuntimeError('git '+' '.join(args)+' fallo en '+directory.name+': '+done.stderr.strip()[:300])
    return done.stdout.strip()

def update_workspace(workspace,apply=True):
    """Lleva cada repo a origin/main. `npm install` del job suele reescribir package-lock.json: eso se
    descarta; cualquier otro cambio local detiene el proceso para no perder trabajo."""
    report=[]
    for name in REPOS:
        directory=workspace/name
        changed={line.split(None,1)[-1].strip() for line in git(directory,'status','--porcelain','--untracked-files=no').splitlines() if line.strip()}
        if changed-{'package-lock.json'}:raise RuntimeError(name+' tiene cambios locales sin guardar ('+', '.join(sorted(changed-{'package-lock.json'}))[:200]+'); no se toco nada')
        git(directory,'fetch','origin','main')
        target=git(directory,'rev-parse','--short','origin/main')
        report.append((name,git(directory,'rev-parse','--short','HEAD'),target))
        if apply:git(directory,'checkout','-f','--detach','origin/main')
    return report

def write_private(path,content,mode=0o600):
    if path.is_symlink():raise RuntimeError('Enlace simbolico inesperado: '+str(path))
    temporary=path.with_name(path.name+'.new')
    temporary.write_text(content);os.chmod(temporary,mode);os.chown(temporary,0,0);os.replace(temporary,path)

def prepare(args,workspace=WORKSPACE,sbin=SBIN_DEPLOYER,env_file=ENV_FILE,backups=BACKUPS):
    if not sbin.exists():raise RuntimeError('No existe '+str(sbin))
    sbin_new,sbin_changed=relax_deployer(sbin.read_text())
    print('  '+sbin.name+': '+('se quitaria el guard KOVA' if sbin_changed else 'sin guard KOVA (nada que quitar)'))
    print('  '+str(env_file)+': se agregaria TASKTRACKER_USER/TASKTRACKER_PASSWORD_HASH y se quitaria SPRING_PROFILES_ACTIVE')
    zip_path=workspace/ZIP_DEPLOYER;zip_new,zip_changed=('',False)
    if args.workspace:
        zip_new,zip_changed=patch_zip_deployer(zip_path.read_text())
        for name,before,after in update_workspace(workspace,apply=not args.check_only):print('  '+name+': '+before+(' pasaria a ' if args.check_only else ' -> ')+after)
        print('  '+ZIP_DEPLOYER+': '+('se parchara' if zip_changed else 'ya esta parchado'))
    if args.check_only:print('CHECK_ONLY: no se escribio nada');return
    if os.geteuid()!=0:raise RuntimeError('Ejecuta con sudo')
    first=getpass.getpass('Contrasena nueva para '+args.username+' (min. 12 caracteres): ')
    if len(first)<12:raise ValueError('Usa al menos 12 caracteres')
    if getpass.getpass('Repite la contrasena: ')!=first:raise ValueError('Las contrasenas no coinciden')
    new_env=rewrite_env(env_file.read_text(),args.username,hash_password(first));del first
    folder=backups/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ');folder.mkdir(parents=True,mode=0o700)
    for source in (sbin,env_file):(folder/source.name).write_text(source.read_text());os.chmod(folder/source.name,0o600)
    write_private(env_file,new_env)
    if sbin_changed:sbin.write_text(sbin_new);os.chown(sbin,0,0);os.chmod(sbin,0o755)
    if zip_changed:(folder/ZIP_DEPLOYER).write_text(zip_path.read_text());zip_path.write_text(zip_new)
    print('LISTO: copias en '+str(folder)+'. Ahora ejecuta el job task-tracker-build en Jenkins (Construir ahora).')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__,formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--username',default='sergio');parser.add_argument('--check-only',action='store_true');parser.add_argument('--workspace',action='store_true')
    args=parser.parse_args()
    try:prepare(args)
    except (ValueError,RuntimeError,OSError) as error:print('FALLO: '+str(error));raise SystemExit(1)

if __name__=='__main__':main()
