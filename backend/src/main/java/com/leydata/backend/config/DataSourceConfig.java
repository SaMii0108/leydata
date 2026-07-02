package com.leydata.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean("writeDataSource")
    @ConfigurationProperties("spring.datasource.write")
    public HikariDataSource writeDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean("readDataSource")
    @ConfigurationProperties("spring.datasource.read")
    public HikariDataSource readDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("writeDataSource") DataSource write,
            @Qualifier("readDataSource") DataSource read) {

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setTargetDataSources(Map.of(
                DataSourceType.WRITE, write,
                DataSourceType.READ, read
        ));
        // DDL de arranque (ddl-auto=update) siempre va al primary
        routing.setDefaultTargetDataSource(write);
        routing.afterPropertiesSet();

        // Garantiza que determineCurrentLookupKey() se ejecute DESPUÉS
        // de que JpaTransactionManager active el flag readOnly en el sync manager
        return new LazyConnectionDataSourceProxy(routing);
    }
}
