#!/usr/bin/env python3
"""Real host SQLite only. Executes production migration SQL; does not substitute for Room."""
from pathlib import Path
import hashlib, json, re, resource, sqlite3, tempfile, time, unittest
ROOT=Path(__file__).resolve().parents[2]
SQL_SOURCE=ROOT/'app/src/main/java/com/unicornwhodev/visiondatasetstudio/data/db/SchemaMigrations.kt'

def migration(name):
    text=SQL_SOURCE.read_text();part=text.split('val '+name+'=listOf(',1)[1].split('\n    )',1)[0]
    return [json.loads(line.strip().rstrip(',')) for line in part.splitlines() if line.strip()]

def old(db, version):
    db.executescript((ROOT/f'app/src/test/resources/legacy-v{version}.sql').read_text())
    db.execute('PRAGMA user_version='+str(version))

def insert(db,table,**overrides):
    row={}
    for _,name,kind,notnull,default,pk in db.execute(f'PRAGMA table_info({table})'):
        if name in overrides:row[name]=overrides[name]
        elif default is not None:continue
        elif notnull:row[name]='' if kind=='TEXT' else 0
    cols=','.join(row);values=','.join('?' for _ in row)
    db.execute(f'INSERT INTO {table} ({cols}) VALUES ({values})',tuple(row.values()))

def seed(db):
    insert(db,'projects',id=7,name='corpus',lastRowCursor=123456,settingsJson='{}')
    insert(db,'samples',sampleId='sample',projectId=7,batchNumber=1,annotationStatus='VALIDATED',acquisitionStatus='AVAILABLE',syncStatus='NOT_EXPORTED',phash=-9223372036854775808)
    insert(db,'annotations',sampleId='sample',dataJson='{"points":[{"id":"human","x":0.2,"y":0.8,"label":"object"}]}')
    insert(db,'batches',projectId=7,batchNumber=1,status='PUBLISHING',totalCases=1)
    db.commit()

def migrate(db,version):
    with db:
        if version==1:
            for sql in migration('from1to2'):db.execute(sql)
        for sql in migration('from2to3'):db.execute(sql)
        db.execute('PRAGMA user_version=3')

class MigrationTests(unittest.TestCase):
    def create(self,v):
        db=sqlite3.connect(':memory:');self.addCleanup(db.close);old(db,v);seed(db);return db
    def test_v1_migrates_without_loss(self):
        db=self.create(1);before=db.execute('SELECT dataJson FROM annotations').fetchone();migrate(db,1)
        self.assertEqual(before,db.execute('SELECT dataJson FROM annotations').fetchone())
        self.assertEqual((123456,),db.execute('SELECT lastRowCursor FROM projects').fetchone())
        self.assertEqual((-9223372036854775808,),db.execute('SELECT phash FROM samples').fetchone())
        self.assertEqual((3,),db.execute('PRAGMA user_version').fetchone())
    def test_v2_migrates_without_loss(self):
        db=self.create(2);migrate(db,2);self.assertEqual(1,db.execute('SELECT COUNT(*) FROM samples').fetchone()[0])
    def test_uncertain_v3_push_becomes_conflict(self):
        db=self.create(2);migrate(db,2);self.assertEqual(('CONFLICT',None,None),db.execute('SELECT status,remoteParentCommit,remoteReceiptJson FROM batches').fetchone())
    def test_no_invented_verification(self):
        db=self.create(1);migrate(db,1);self.assertEqual((None,None),db.execute('SELECT verificationKind,verifiedArchiveSha256 FROM batches').fetchone())
    def test_failed_migration_rolls_back(self):
        db=self.create(2)
        with self.assertRaises(sqlite3.OperationalError):
            with db:
                db.execute('BEGIN IMMEDIATE')
                db.execute(migration('from2to3')[0]);db.execute('INSERT INTO no_such_table VALUES (1)')
        self.assertNotIn('remoteRepoId',[x[1] for x in db.execute('PRAGMA table_info(batches)')])
        self.assertEqual((2,),db.execute('PRAGMA user_version').fetchone())
    def test_explicit_sqlite_default_values(self):
        db=self.create(1);migrate(db,1);self.assertEqual(('{}',),db.execute('SELECT settingsJson FROM projects').fetchone())
        self.assertEqual(('identity',),db.execute('SELECT imageTransform FROM samples').fetchone())
    def test_indexes_exist(self):
        db=self.create(1);migrate(db,1);names=[x[1] for x in db.execute('PRAGMA index_list(samples)')]
        self.assertIn('index_samples_projectId_batchNumber_sourceOrdinal',names)
        self.assertIn('index_samples_projectId_sha256',names)
    def test_page_query_uses_keyset_index(self):
        db=self.create(1);migrate(db,1)
        details=str(db.execute('EXPLAIN QUERY PLAN SELECT * FROM source_entries WHERE projectId=? AND ordinal>=? ORDER BY ordinal LIMIT ?', (7,10,100)).fetchall())
        self.assertIn('INDEX',details);self.assertNotIn('TEMP B-TREE',details)
    def test_legacy_table_definitions_match_after_migration(self):
        a=self.create(1);b=self.create(2);migrate(a,1);migrate(b,2)
        for table in ['projects','samples','annotations','batches','audit_logs','model_profiles','source_entries']:
            # Column order is immaterial to Room TableInfo; types/nullability/defaults/PK positions must agree.
            left={row[1]:row[2:] for row in a.execute('PRAGMA table_info('+table+')')}
            right={row[1]:row[2:] for row in b.execute('PRAGMA table_info('+table+')')}
            self.assertEqual(left,right,table)

def stress():
    n=200000;report={'scope':'HOST SQLite metadata index only; not Android Room, image decoding or full corpus workflow','rows':n,'sqlite':sqlite3.sqlite_version}
    with tempfile.TemporaryDirectory(prefix='vds-sqlite-stress-') as tmp:
        path=Path(tmp)/'index.db';db=sqlite3.connect(path);old(db,2);migrate(db,2)
        db.execute('PRAGMA journal_mode=WAL');db.execute('PRAGMA cache_size=-8192')
        start=time.perf_counter()
        for start_row in range(0,n,256):
            with db:db.executemany('INSERT INTO source_entries VALUES (?,?,?,?,?,?,?)',((7,i,f'asset-{i}',f'https://example.invalid/images/{i}.jpg',i,f'group-{i//50}',None) for i in range(start_row,min(start_row+256,n))))
        report['insert_seconds']=time.perf_counter()-start
        times=[]
        for offset in [0,100,10000,100000,199900]*10:
            start=time.perf_counter();rows=db.execute('SELECT * FROM source_entries WHERE projectId=? AND ordinal>=? ORDER BY ordinal LIMIT ?', (7,offset,100)).fetchall()
            times.append((time.perf_counter()-start)*1000);assert len(rows)==100 and rows[0][1]==offset
        report['page_100_p50_ms']=sorted(times)[24];report['page_100_p95_ms']=sorted(times)[47]
        assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
        db.close();reopened=sqlite3.connect(path);assert reopened.execute('SELECT COUNT(*) FROM source_entries').fetchone()[0]==n;reopened.close()
        report['database_bytes']=path.stat().st_size
    report['process_max_rss_kib']=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    report['migration_source_sha256']=hashlib.sha256(SQL_SOURCE.read_bytes()).hexdigest()
    dest=ROOT/'test-results/v4.2/sqlite-stress.json';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(MigrationTests))
    if not result.wasSuccessful():raise SystemExit(1)
    stress()
