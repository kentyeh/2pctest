package p2;

import java.sql.SQLException;
import javax.sql.XAConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Scope;

/**
 *
 * @author kent
 */
@Configuration
@ImportResource({"classpath:testContext.xml"})
public class AppContext {

    @Autowired
    JdbcDataSource dataSource1;
    @Autowired
    JdbcDataSource dataSource2;

    @Bean(destroyMethod = "close")
    @Scope(value = "prototype")
    public XAConnection xconn1() throws SQLException {
        return dataSource1.getXAConnection();
    }

    @Bean(destroyMethod = "close")
    @Scope(value = "prototype")
    public XAConnection xconn2() throws SQLException {
        return dataSource2.getXAConnection();
    }

}
