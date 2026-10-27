"""Prueba el script contra archivos y repos git desechables (sin tocar el servidor real)."""
import argparse,io,os,subprocess,tempfile,unittest
from contextlib import redirect_stdout
from pathlib import Path
import preparar_login_local as tool

ZIP_SCRIPT='''for _ in {1..45}; do
  if curl --fail --silent --max-time 2 http://127.0.0.1:8081/api/projects >/dev/null; then
    break
  fi
done
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8081/api/projects >/dev/null || { exit 1; }
'''
HASH='$2y$12$'+'a'*53

def sh(*cmd,cwd=None):subprocess.run(cmd,cwd=cwd,check=True,capture_output=True)

class PrepareTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.base=Path(self.tmp.name)
        self.workspace=self.base/'ws';self.workspace.mkdir()
        for name in tool.REPOS:
            origin=self.base/(name+'-origin');work=self.base/(name+'-seed')
            sh('git','init','-q','-b','main',str(work));sh('git','-C',str(work),'config','user.email','t@t');sh('git','-C',str(work),'config','user.name','t')
            (work/'f').write_text('v1');sh('git','-C',str(work),'add','.');sh('git','-C',str(work),'commit','-qm','v1')
            sh('git','clone','-q','--bare',str(work),str(origin));sh('git','clone','-q',str(origin),str(self.workspace/name))
            sh('git','-C',str(self.workspace/name),'checkout','-q','--detach')
            (work/'f').write_text('v2');sh('git','-C',str(work),'commit','-qam','v2');sh('git','-C',str(work),'push','-q',str(origin),'main')
        (self.workspace/tool.ZIP_DEPLOYER).write_text(ZIP_SCRIPT)
        self.sbin=self.base/'sbin.sh';self.sbin.write_text('#!/bin/bash\n')

    def head(self,name):return subprocess.run(['git','-C',str(self.workspace/name),'rev-parse','--short','HEAD'],capture_output=True,text=True).stdout.strip()
    def origin_head(self,name):return subprocess.run(['git','-C',str(self.base/(name+'-origin')),'rev-parse','--short','main'],capture_output=True,text=True).stdout.strip()

    def test_workspace_moves_to_origin_main(self):
        before={n:self.head(n) for n in tool.REPOS}
        tool.update_workspace(self.workspace)
        for name in tool.REPOS:
            self.assertNotEqual(before[name],self.head(name));self.assertEqual(self.head(name),self.origin_head(name))

    def test_workspace_refuses_local_changes(self):
        (self.workspace/'taskTrackerBack/f').write_text('mine')
        with self.assertRaises(RuntimeError):tool.update_workspace(self.workspace)
        self.assertEqual((self.workspace/'taskTrackerBack/f').read_text(),'mine')

    def test_lockfile_changes_are_discarded_but_other_changes_block(self):
        (self.workspace/'taskTrackerFront/package-lock.json').write_text('x');sh('git','-C',str(self.workspace/'taskTrackerFront'),'add','-N','package-lock.json')
        sh('git','-C',str(self.workspace/'taskTrackerFront'),'commit','-qam','lock');sh('git','-C',str(self.workspace/'taskTrackerFront'),'reset','-q','--hard','HEAD')
        (self.workspace/'taskTrackerFront/package-lock.json').write_text('rewritten by npm install')
        tool.update_workspace(self.workspace)
        self.assertEqual(self.head('taskTrackerFront'),self.origin_head('taskTrackerFront'))

    def test_check_only_changes_nothing(self):
        before={n:self.head(n) for n in tool.REPOS};text=(self.workspace/tool.ZIP_DEPLOYER).read_text()
        out=io.StringIO()
        with redirect_stdout(out):
            tool.prepare(argparse.Namespace(check_only=True,username='sergio',workspace=True),self.workspace,self.sbin,self.base/'tt.env',self.base/'bk')
        self.assertEqual(before,{n:self.head(n) for n in tool.REPOS});self.assertEqual(text,(self.workspace/tool.ZIP_DEPLOYER).read_text())
        self.assertIn('pasaria a',out.getvalue());self.assertIn('no se escribio nada',out.getvalue())

    def test_zip_script_patch_is_idempotent_and_valid_bash(self):
        new,changed=tool.patch_zip_deployer(ZIP_SCRIPT)
        self.assertTrue(changed);self.assertEqual(new.count('== 401 ]]'),2);self.assertNotIn('--fail',new)
        self.assertEqual(tool.patch_zip_deployer(new),(new,False))
        subprocess.run(['bash','-n','-c',new],check=True)
        with self.assertRaises(ValueError):tool.patch_zip_deployer('nada que ver')

    def test_sbin_guard_removal_matches_the_kova_cutover(self):
        try:import apply_managed_applications as cutover
        except ImportError:self.skipTest('el repo kova no esta en la ruta')
        legacy='test -f "$JAR_FILE"\ncurl --fail --silent --max-time 2 http://127.0.0.1:8081/api/projects >/dev/null\ncurl --fail --silent --show-error --max-time 5 http://127.0.0.1:8081/api/projects >/dev/null\n'
        relaxed,changed=tool.relax_deployer(cutover.harden_deployer(legacy,'tasktracker'))
        self.assertTrue(changed);self.assertNotIn('KOVA',relaxed)

    def test_default_check_only_does_not_touch_the_workspace(self):
        before={n:self.head(n) for n in tool.REPOS};out=io.StringIO()
        with redirect_stdout(out):
            tool.prepare(argparse.Namespace(check_only=True,username='sergio',workspace=False),self.workspace,self.sbin,self.base/'tt.env',self.base/'bk')
        self.assertEqual(before,{n:self.head(n) for n in tool.REPOS});self.assertNotIn('taskTrackerBack',out.getvalue())

    @unittest.skipUnless(os.geteuid()==0,'necesita root para chown')
    def test_full_run_without_workspace(self):
        import apply_managed_applications as cutover
        legacy='test -f "$JAR_FILE"\ncurl --fail --silent --max-time 2 http://127.0.0.1:8081/api/projects >/dev/null\ncurl --fail --silent --show-error --max-time 5 http://127.0.0.1:8081/api/projects >/dev/null\n'
        self.sbin.write_text(cutover.harden_deployer(legacy,'tasktracker'))
        env=self.base/'tt.env';env.write_text('TASKTRACKER_DB_USER=app\nSPRING_PROFILES_ACTIVE=kova-personal\n')
        from unittest.mock import patch
        with patch.object(tool.getpass,'getpass',return_value='una-clave-larga-123'),patch.object(tool,'hash_password',return_value=HASH),redirect_stdout(io.StringIO()):
            tool.prepare(argparse.Namespace(check_only=False,username='sergio',workspace=False),self.workspace,self.sbin,env,self.base/'bk')
        self.assertNotIn('KovaSecurity',self.sbin.read_text());self.assertIn('== 401 ]]',self.sbin.read_text())
        self.assertIn('TASKTRACKER_USER=sergio',env.read_text());self.assertNotIn('kova-personal',env.read_text());self.assertEqual(oct(env.stat().st_mode)[-3:],'600')
        self.assertEqual(len(list((self.base/'bk').iterdir())),1)

    def test_env_rewrite(self):
        old='TASKTRACKER_DB_USER=app\nSPRING_PROFILES_ACTIVE=kova-personal\nSERVER_ADDRESS=127.0.0.1\n'
        new=tool.rewrite_env(old,'sergio',HASH)
        self.assertNotIn('SPRING_PROFILES_ACTIVE',new);self.assertIn('TASKTRACKER_DB_USER=app',new);self.assertEqual(tool.rewrite_env(new,'sergio',HASH),new)
        with self.assertRaises(ValueError):tool.rewrite_env(old,'sergio','plano')

if __name__=='__main__':unittest.main()
