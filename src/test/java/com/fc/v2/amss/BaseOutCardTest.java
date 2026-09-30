package com.fc.v2.amss;

import java.util.Date;

import javax.sql.DataSource;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAmssOutCardMapper;
import com.fc.v2.mapper.auto.TAmssOutCardStageMapper;
import com.fc.v2.service.ITAmssOutCardService;
import com.fc.v2.service.impl.TAmssOutCardServiceImpl;

/**
 * 缴回卡状态机测试基座：H2 内存库（MySQL 兼容）里跑真 SQL、真唯一约束，
 * 经 SqlSessionTemplate + 事务模板，与生产同一套 mapper 与异常翻译。
 */
public abstract class BaseOutCardTest {

    protected static DataSource dataSource;
    protected static SqlSessionFactory factory;
    protected static SqlSessionTemplate sqlSession;
    protected static TransactionTemplate tx;

    @BeforeAll
    static void initInfra() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:outcard;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        ds.setUser("sa");
        dataSource = ds;

        MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
        fb.setDataSource(dataSource);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        fb.setConfiguration(cfg);

        com.baomidou.mybatisplus.core.config.GlobalConfig gc =
                new com.baomidou.mybatisplus.core.config.GlobalConfig();
        gc.setMetaObjectHandler(new MetaObjectHandler() {
            @Override
            public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                this.fillStrategy(metaObject, "createBy", "test");
                this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
            }

            @Override
            public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                this.fillStrategy(metaObject, "updateBy", "test");
                this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
            }
        });
        fb.setGlobalConfig(gc);

        factory = fb.getObject();
        factory.getConfiguration().addMapper(TAmssOutCardMapper.class);
        factory.getConfiguration().addMapper(TAmssOutCardStageMapper.class);

        sqlSession = new SqlSessionTemplate(factory, ExecutorType.SIMPLE);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        resetSchema();
    }

    @BeforeEach
    void resetDb() {
        resetSchema();
    }

    private static void resetSchema() {
        try (java.sql.Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c,
                    new org.springframework.core.io.ByteArrayResource(
                            "DROP TABLE IF EXISTS t_amss_out_card_stage; DROP TABLE IF EXISTS t_amss_out_card;"
                                    .getBytes("UTF-8")));
            ScriptUtils.executeSqlScript(c, new ClassPathResource("schema-h2.sql"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 装一个走 SqlSessionTemplate、带 Spring 事务边界（@Transactional 生效）的 service */
    protected ITAmssOutCardService newService() {
        TAmssOutCardServiceImpl raw = new TAmssOutCardServiceImpl();
        return wrap(raw);
    }

    /** 给装好 mapper 的自定义实例套上同款事务代理 */
    protected ITAmssOutCardService wrap(TAmssOutCardServiceImpl raw) {
        ReflectionTestUtils.setField(raw, "amssOutCardMapper",
                sqlSession.getMapper(TAmssOutCardMapper.class));
        ReflectionTestUtils.setField(raw, "amssOutCardStageMapper",
                sqlSession.getMapper(TAmssOutCardStageMapper.class));

        TransactionProxyFactoryBean proxy = new TransactionProxyFactoryBean();
        proxy.setTransactionManager(new DataSourceTransactionManager(dataSource));
        proxy.setTarget(raw);
        java.util.Properties attrs = new java.util.Properties();
        attrs.setProperty("*", "PROPAGATION_REQUIRED,-java.lang.Exception");
        proxy.setTransactionAttributes(attrs);
        proxy.afterPropertiesSet();
        return (ITAmssOutCardService) proxy.getObject();
    }
}
