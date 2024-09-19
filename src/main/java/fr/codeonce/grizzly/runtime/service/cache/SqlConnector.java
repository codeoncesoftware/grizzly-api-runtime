/*
 * Copyright © 2020 CodeOnce Software (https://www.codeonce.fr/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.codeonce.grizzly.runtime.service.cache;

import com.couchbase.client.java.env.ClusterEnvironment;
import fr.codeonce.grizzly.common.runtime.Provider;
import fr.codeonce.grizzly.runtime.service.feign.DBSource;
import fr.codeonce.grizzly.runtime.service.feign.FeignDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@DependsOnDatabaseInitialization
@Service(value = "sqlConnector")
public class SqlConnector implements ICache<DataSource> {

    @Autowired
    @Qualifier("runtimeCryptoHandler")
    private CryptoHelper encryption;

    @Autowired
    private FeignDiscovery feignDiscovery;

    private final ClusterEnvironment environment = ClusterEnvironment.create();

    private static final Logger log = LoggerFactory.getLogger(SqlConnector.class);

    @Cacheable(value = "datasource", key = "dbsourceId")
    public DataSource getClientDatasource(String dbsourceId) throws Exception {
        DBSource db = feignDiscovery.getDBSource(dbsourceId);
        encryption.decrypt(db);
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName(getDriverClassName(db.getProvider()));
        dataSourceBuilder.url(generateConnectionUrl(db));
        return dataSourceBuilder.build();
    }

    @Cacheable(value = "jdbctemplate")
    public JdbcTemplate prepareDatasourceClient(String dbsourceId) throws Exception {
        JdbcTemplate jt = new JdbcTemplate();
        DataSource datasource = getClientDatasource(dbsourceId);
        jt.setDataSource(datasource);
        return jt;
    }

    @Override
    @CachePut(value = "datasource", key = "dbsourceId")
    public DataSource updateCache(String dbsourceId) throws Exception {
        return getClientDatasource(dbsourceId);
    }

    private String generateConnectionUrl(DBSource dto) {
        String url = "";
        if (dto.getProvider().name().toLowerCase().equals("sqlserver")) {
            url = "jdbc:" + getProviderName(dto.getProvider()) + "://" + dto.getHost() + ":" + dto.getPort()
                    + ";databaseName=" + dto.getDatabase() + ";user=" + dto.getUsername() + ";password="
                    + getSafeValue(dto.getPassword());
        } else {
            url = "jdbc:" + getProviderName(dto.getProvider()) + "://" + dto.getHost() + ":" + dto.getPort()
                    + "/" + dto.getDatabase() + "?user=" + dto.getUsername() + "&password=" + getSafeValue(dto.getPassword());
        }
        return url;
    }

    private String getDriverClassName(Provider provider) {
        switch (provider) {
            case MYSQL:
                return "com.mysql.jdbc.Driver";
            case MARIADB:
                return "org.mariadb.jdbc.Driver";
            case POSTGRESQL:
                return "org.postgresql.Driver";
            case SQLSERVER:
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:
                return null;
        }
    }


    private String getProviderName(Provider provider) {
        switch (provider) {
            case MYSQL:
                return "mysql";
            case MARIADB:
                return "mysql";
            case POSTGRESQL:
                return "postgresql";
            case SQLSERVER:
                return "sqlserver";
            default:
                return null;
        }
    }

    private String getSafeValue(char[] pwd) {
        return pwd == null ? "" : String.valueOf(pwd);
    }

}
