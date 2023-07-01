package com.github.vssavin.umlib.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Created by vssavin on 25.08.2022.
 */
@Component
public class DataSourceSwitcher {

    private final AbstractRoutingDataSource routingDataSource;
    private RoutingDataSource.DATASOURCE_TYPE previousDataSourceKey;

    public DataSourceSwitcher(AbstractRoutingDataSource routingDataSource) {
        this.routingDataSource = routingDataSource;
        this.previousDataSourceKey = RoutingDataSource.DATASOURCE_TYPE.APPLICATION_DATASOURCE;
    }

    public void switchToUmDataSource() {
        previousDataSourceKey = ((RoutingDataSource)routingDataSource).getDatasourceKey();
        ((RoutingDataSource)routingDataSource).setKey(RoutingDataSource.DATASOURCE_TYPE.UM_DATASOURCE);
    }

    public void switchToApplicationDataSource() {
        previousDataSourceKey = ((RoutingDataSource)routingDataSource).getDatasourceKey();
        ((RoutingDataSource)routingDataSource).setKey(RoutingDataSource.DATASOURCE_TYPE.APPLICATION_DATASOURCE);
        DataSource ds = ((RoutingDataSource)routingDataSource).determineTargetDataSource();
        if (ds == null) ((RoutingDataSource)routingDataSource).setKey(previousDataSourceKey);
    }

    public void switchToPreviousDataSource() {
        RoutingDataSource.DATASOURCE_TYPE currentKey = ((RoutingDataSource)routingDataSource).getDatasourceKey();
        ((RoutingDataSource)routingDataSource).setKey(previousDataSourceKey);
        DataSource ds = ((RoutingDataSource)routingDataSource).determineTargetDataSource();
        if (ds == null) ((RoutingDataSource)routingDataSource).setKey(currentKey);
    }

    public DataSource getCurrentDataSource() {
        return ((RoutingDataSource) routingDataSource).determineTargetDataSource();
    }
}