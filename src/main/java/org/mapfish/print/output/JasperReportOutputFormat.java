/*
 * Copyright (C) 2014  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.output;

import jsr166y.ForkJoinPool;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.mapfish.print.Constants;
import org.mapfish.print.attribute.Attribute;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.config.Template;
import org.mapfish.print.config.WorkingDirectories;
import org.mapfish.print.json.PJsonObject;
import org.mapfish.print.processor.jasper.JasperReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

/**
 * An PDF output format that uses Jasper reports to generate the result.
 *
 * @author Jesse
 * @author sbrunner
 */
public class JasperReportOutputFormat implements OutputFormat {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(JasperReportOutputFormat.class);
    @Autowired
    private ForkJoinPool forkJoinPool;

    @Autowired
    private WorkingDirectories workingDirectories;

    @Override
    public final String getContentType() {
        return "application/pdf";
    }

    @Override
    public final String getFileSuffix() {
        return "pdf";
    }

    @Override
    public final void print(final PJsonObject requestData, final Configuration config, final File configDir, final OutputStream outputStream)
            throws Exception {
        final String templateName = requestData.getString(Constants.JSON_LAYOUT_KEY);

        final Template template = config.getTemplate(templateName);
        final Values values = new Values(requestData, template);

        final File jasperTemplateFile = new File(configDir, template.getJasperTemplate());
        final File jasperTemplateBuild = this.workingDirectories.getBuildFileFor(config, jasperTemplateFile,
                JasperReportBuilder.JASPER_REPORT_COMPILED_FILE_EXT, LOGGER);

        final File jasperTemplateDirectory = jasperTemplateBuild.getParentFile();

        values.put("SUBREPORT_DIR", jasperTemplateDirectory.getAbsolutePath());


        this.forkJoinPool.invoke(template.getProcessorGraph().createTask(values));

        if (template.getIterValue() != null) {
            final List<Map<String, ?>> dataSource = this.forkJoinPool.invoke(new ExecuteIterProcessorsTask(values, template));

            final JRDataSource jrDataSource = new JRMapCollectionDataSource(dataSource);
            final JasperPrint print = JasperFillManager.fillReport(
                    jasperTemplateBuild.getAbsolutePath(),
                    values.getParameters(),
                    jrDataSource);
            JasperExportManager.exportReportToPdfStream(print, outputStream);
        } else if (template.getJdbcUrl() != null && template.getJdbcUser() != null && template.getJdbcPassword() != null) {
            Connection connection = DriverManager.getConnection(
                    template.getJdbcUrl(), template.getJdbcUser(), template.getJdbcPassword());
            final JasperPrint print = JasperFillManager.fillReport(
                    jasperTemplateBuild.getAbsolutePath(),
                    values.getParameters(),
                    connection);
            JasperExportManager.exportReportToPdfStream(print, outputStream);
        } else if (template.getJdbcUrl() != null) {
            Connection connection = DriverManager.getConnection(template.getJdbcUrl());
            final JasperPrint print = JasperFillManager.fillReport(
                    jasperTemplateBuild.getAbsolutePath(),
                    values.getParameters(),
                    connection);
            JasperExportManager.exportReportToPdfStream(print, outputStream);
        } else {
            final JasperPrint print = JasperFillManager.fillReport(
                    jasperTemplateBuild.getAbsolutePath(),
                    values.getParameters(),
                    new JREmptyDataSource());
            JasperExportManager.exportReportToPdfStream(print, outputStream);
        }
    }
}
