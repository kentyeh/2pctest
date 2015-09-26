package p2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.XAConnection;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import org.springframework.beans.factory.annotation.Value;
import org.xadisk.additional.XAFileOutputStreamWrapper;
import org.xadisk.bridge.proxies.interfaces.XAFileSystem;
import org.xadisk.bridge.proxies.interfaces.XAFileSystemProxy;
import org.xadisk.bridge.proxies.interfaces.XASession;
import org.xadisk.filesystem.exceptions.XAApplicationException;
import org.xadisk.filesystem.standalone.StandaloneFileSystemConfiguration;

/**
 * Unit test for simple App.
 */
@ContextConfiguration(classes = p2.AppContext.class)
public class QuickTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private XAConnection xconn1;
    @Autowired
    private XAConnection xconn2;

    @Autowired
    private TransactionManager transactionManager;

    @Value("#{appProperies['targetFolder']}")
    private String targetFolder;

    @BeforeClass
    public void setup() {
        logger.info(getClass().getName() + " startup");
    }

    @AfterClass
    public void teardown() {
        logger.info(getClass().getName() + " stop");
    }

    public void testRollbackWithJdbi() throws SQLException, XAException {

    }

    @Test
    public void testRollbackWithRaw() throws SQLException, XAException {
        XAResource oxar1 = xconn1.getXAResource();
        XAResource oxar2 = xconn2.getXAResource();
        MyXid xid1 = new MyXid(100, new byte[]{0x01}, new byte[]{0x01});
        MyXid xid2 = new MyXid(100, new byte[]{0x01}, new byte[]{0x02});

        boolean stopCommit = false;

        try {
            Connection conn = xconn1.getConnection();
            Statement stat = conn.createStatement();
            logger.debug("DB1 before insert");
            oxar1.start(xid1, XAResource.TMNOFLAGS);
            stat.executeUpdate("insert into member(name) values('rollback')");
            oxar1.end(xid1, XAResource.TMSUCCESS);
            logger.debug("DB1 insertion success");
        } catch (SQLException | XAException e) {
            oxar1.end(xid1, XAResource.TMFAIL);
            stopCommit = true;
            logger.error("DB1 faild with " + e.getMessage(), e);
        }
        try {
            Connection conn = xconn2.getConnection();
            Statement stat = conn.createStatement();
            logger.error("DB2 before insert Unique violation");
            oxar2.start(xid2, XAResource.TMNOFLAGS);
            stat.executeUpdate("insert into member(name) values('Rollback')");
            oxar2.end(xid2, XAResource.TMSUCCESS);
            logger.debug("DB2 insertion success");
        } catch (SQLException | XAException e) {
            oxar2.end(xid2, XAResource.TMFAIL);
            stopCommit = true;
            logger.error("DB2 faild with " + e.getMessage(), e);
        }
        if (stopCommit) {
            logger.debug("DB1 before rollback");
            oxar1.rollback(xid1);
            logger.debug("DB1 after rollback and DB2 before rollback");
            oxar2.rollback(xid2);
            logger.debug("DB1 after rollback");
        } else {
            int prp1 = oxar1.prepare(xid1);
            int prp2 = oxar2.prepare(xid2);
            logger.info("Return value of prepare 1 is " + prp1);
            logger.info("Return value of prepare 2 is " + prp2);
            boolean do_commit = true;

            if (!((prp1 == XAResource.XA_OK) || (prp1 == XAResource.XA_RDONLY))) {
                do_commit = false;
            }

            if (!((prp2 == XAResource.XA_OK) || (prp2 == XAResource.XA_RDONLY))) {
                do_commit = false;
            }
            logger.info("do_commit is " + do_commit);
            logger.info("Is oxar1 same as oxar2 ? " + oxar1.isSameRM(oxar2));
            if (prp1 == XAResource.XA_OK) {
                if (do_commit) {
                    oxar1.commit(xid1, false);
                    logger.debug("\tfirst commit");
                } else {
                    oxar1.rollback(xid1);
                    logger.debug("\tfirstrollback");
                }
            }

            if (prp2 == XAResource.XA_OK) {
                if (do_commit) {
                    oxar2.commit(xid2, false);
                    logger.debug("\tsecond commit");
                } else {
                    oxar2.rollback(xid2);
                    logger.debug("\tsecond rollback");
                }
            }
        }
        try (Connection conn = xconn1.getConnection();
                Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT id,name from member where name= 'rollback'");) {
            assertThat("2PC Rollback Faild", rs.next(), is(Boolean.FALSE));
        }
    }

    @Test
    public void testCommitWithRaw() throws SQLException, XAException {
        XAResource oxar1 = xconn1.getXAResource();
        XAResource oxar2 = xconn2.getXAResource();
        MyXid xid1 = new MyXid(0, new byte[]{0x01}, new byte[]{0x01});
        MyXid xid2 = new MyXid(0, new byte[]{0x01}, new byte[]{0x02});
        boolean stopCommit = false;

        try {
            Connection conn1 = xconn1.getConnection();
            Statement stat = conn1.createStatement();
            logger.debug("DB1 before insert");
            oxar1.start(xid1, XAResource.TMNOFLAGS);
            stat.executeUpdate("insert into member(name) values('Commit')");
            oxar1.end(xid1, XAResource.TMSUCCESS);
            logger.debug("DB1 insertion success");
        } catch (SQLException | XAException e) {
            oxar1.end(xid1, XAResource.TMFAIL);
            stopCommit = true;
            logger.error("DB1 faild with " + e.getMessage(), e);
        }
        try {
            Connection conn2 = xconn2.getConnection();
            Statement stat = conn2.createStatement();
            logger.debug("DB2 before insert");
            oxar2.start(xid2, XAResource.TMNOFLAGS);
            stat.executeUpdate("insert into member(name) values('Commit')");
            oxar2.end(xid2, XAResource.TMSUCCESS);
            logger.debug("DB2 insertion success");
        } catch (SQLException | XAException e) {
            oxar2.end(xid2, XAResource.TMFAIL);
            stopCommit = true;
            logger.error("DB2 faild with " + e.getMessage(), e);
        }
        if (stopCommit) {
            oxar1.rollback(xid1);
            oxar2.rollback(xid2);
        } else {
            int prp1 = oxar1.prepare(xid1);
            int prp2 = oxar2.prepare(xid2);
            logger.info("Return value of prepare 1 is " + prp1);
            logger.info("Return value of prepare 2 is " + prp2);
            boolean do_commit = true;

            if (!((prp1 == XAResource.XA_OK) || (prp1 == XAResource.XA_RDONLY))) {
                do_commit = false;
            }

            if (!((prp2 == XAResource.XA_OK) || (prp2 == XAResource.XA_RDONLY))) {
                do_commit = false;
            }
            logger.info("do_commit is " + do_commit);
            logger.info("Is oxar1 same as oxar2 ? " + oxar1.isSameRM(oxar2));
            if (prp1 == XAResource.XA_OK) {
                if (do_commit) {
                    oxar1.commit(xid1, false);
                    logger.debug("\tfirst commit");
                } else {
                    oxar1.rollback(xid1);
                    logger.debug("\tfirstrollback");
                }
            }

            if (prp2 == XAResource.XA_OK) {
                if (do_commit) {
                    oxar2.commit(xid2, false);
                    logger.debug("\tsecond commit");
                } else {
                    oxar2.rollback(xid2);
                    logger.debug("\tsecond rollback");
                }
            }
        }
        try (Connection conn = xconn1.getConnection();
                Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT id,name from member where name= 'Commit'");) {
            assertThat("2PC Commit Faild", rs.next(), is(Boolean.TRUE));
        }
        try (Connection conn = xconn2.getConnection();
                Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT id,name from member where name= 'Commit'");) {
            assertThat("2PC Commit Faild", rs.next(), is(Boolean.TRUE));
        }
    }

    @Test
    public void testRollbackWithTxm() throws SQLException, NotSupportedException,
            SystemException, RollbackException, HeuristicMixedException, HeuristicRollbackException,
            InterruptedException,XAApplicationException, IOException  {
        XAResource oxar1 = xconn1.getXAResource();
        XAResource oxar2 = xconn2.getXAResource();
        
        StandaloneFileSystemConfiguration configuration = new StandaloneFileSystemConfiguration(targetFolder, "2");
        XAFileSystem xafs = XAFileSystemProxy.bootNativeXAFileSystem(configuration);
        xafs.waitForBootup(-1);
        XASession xaSession = xafs.createSessionForXATransaction();
        XAResource xarXADisk = xaSession.getXAResource();
        
        transactionManager.begin();
        transactionManager.getTransaction().enlistResource(oxar1);
        transactionManager.getTransaction().enlistResource(oxar2);
        transactionManager.getTransaction().enlistResource(xarXADisk);
        try {
            Connection conn1 = xconn1.getConnection();
            Statement stat = conn1.createStatement();
            logger.debug("DB1 before insert");
            stat.executeUpdate("insert into member(name) values('rollbackAtm')");
            transactionManager.getTransaction().delistResource(oxar1, XAResource.TMSUCCESS);
            logger.debug("DB1 insertion success");

            logger.error("++++++++++++++ Disk op start ++++++++++++++");
            File file = Paths.get(targetFolder, "Rollback.txt").toFile();
            xaSession.createFile(file, false);
            try (Writer wr = new BufferedWriter(new OutputStreamWriter(new XAFileOutputStreamWrapper(xaSession.createXAFileOutputStream(file, false))))) {
                wr.write("Rollback");
                wr.flush();
            }
            transactionManager.getTransaction().delistResource(xarXADisk, XAResource.TMSUCCESS);
            logger.error("++++++++++++++ Disk op stop ++++++++++++++");
            
            
            Connection conn2 = xconn2.getConnection();
            stat = conn2.createStatement();
            logger.debug("DB2 before insert");
            stat.executeUpdate("insert into member(name) values('Rollback')");
            logger.debug("DB2 insertion success");
            
            transactionManager.commit();
        } catch (SQLException e) {
            logger.debug("Transaction Rollback");
            transactionManager.rollback();
        } finally {
            xafs.shutdown();
        }
        try (Connection conn = xconn1.getConnection();
                Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT id,name from member where name= 'rollback'");) {
            assertThat("2PC Rollback Faild", rs.next(), is(Boolean.FALSE));
        }
        assertThat("2PC Commit Faild", Files.exists(Paths.get(targetFolder, "Rollback.txt")), is(Boolean.FALSE));
    }

    @Test
    public void testCommitWithTxm() throws SQLException, NotSupportedException,
            SystemException, RollbackException, HeuristicMixedException, HeuristicRollbackException,
            InterruptedException, XAApplicationException, IOException {
        XAResource oxar1 = xconn1.getXAResource();
        XAResource oxar2 = xconn2.getXAResource();
        StandaloneFileSystemConfiguration configuration = new StandaloneFileSystemConfiguration(targetFolder, "3");
        XAFileSystem xafs = XAFileSystemProxy.bootNativeXAFileSystem(configuration);
        xafs.waitForBootup(-1);
        XASession xaSession = xafs.createSessionForXATransaction();
        XAResource xarXADisk = xaSession.getXAResource();

        transactionManager.begin();
        transactionManager.getTransaction().enlistResource(oxar1);
        transactionManager.getTransaction().enlistResource(oxar2);
        transactionManager.getTransaction().enlistResource(xarXADisk);
        try {
            Connection conn1 = xconn1.getConnection();
            Statement stat = conn1.createStatement();
            logger.debug("DB1 before insert");
            stat.executeUpdate("insert into member(name) values('AtmCommit')");
            logger.debug("DB1 insertion success");
            logger.error("************** remove res 1 invoke XAResource.end immedidate. **************");
            transactionManager.getTransaction().delistResource(oxar1, XAResource.TMSUCCESS);
            logger.error("************** remove res 1**************");

            Connection conn2 = xconn2.getConnection();
            stat = conn2.createStatement();
            logger.debug("DB2 before insert");
            stat.executeUpdate("insert into member(name) values('AtmCommit')");
            logger.debug("DB2 insertion success");
            logger.error("************** remove res 1 invoke XAResource.end immedidate. **************");
            transactionManager.getTransaction().delistResource(oxar2, XAResource.TMSUCCESS);
            logger.error("************** remove res 1**************");
            
            logger.error("++++++++++++++ Disk op start ++++++++++++++");
            File file = Paths.get(targetFolder, "Commit.txt").toFile();
            xaSession.createFile(file, false);
            try (Writer wr = new BufferedWriter(new OutputStreamWriter(new XAFileOutputStreamWrapper(xaSession.createXAFileOutputStream(file, false))))) {
                wr.write("Commit");
                wr.flush();
            }
            transactionManager.getTransaction().delistResource(xarXADisk, XAResource.TMSUCCESS);
            logger.error("++++++++++++++ Disk op stop ++++++++++++++");
            
            transactionManager.commit();
        } catch (SQLException e) {
            logger.debug("Transaction Rollback");
            transactionManager.rollback();
        } finally {
            xafs.shutdown();
        }

        try (Connection conn = xconn1.getConnection();
                Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT id,name from member where name= 'AtmCommit'");) {
            assertThat("2PC Commit Faild", rs.next(), is(Boolean.TRUE));
        }
        try (Connection conn = xconn2.getConnection();
                Statement stat = conn.createStatement();
                ResultSet rs = stat.executeQuery("SELECT id,name from member where name= 'AtmCommit'");) {
            assertThat("2PC Rollback Faild", rs.next(), is(Boolean.TRUE));
        }
        assertThat("2PC Commit Faild", Files.exists(Paths.get(targetFolder, "Commit.txt")), is(Boolean.TRUE));
    }
}
