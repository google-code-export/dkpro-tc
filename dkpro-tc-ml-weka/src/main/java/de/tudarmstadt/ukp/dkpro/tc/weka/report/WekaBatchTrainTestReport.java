/**
 * Copyright 2014
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package de.tudarmstadt.ukp.dkpro.tc.weka.report;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

import de.tudarmstadt.ukp.dkpro.lab.reporting.BatchReportBase;
import de.tudarmstadt.ukp.dkpro.lab.reporting.FlexTable;
import de.tudarmstadt.ukp.dkpro.lab.storage.StorageService;
import de.tudarmstadt.ukp.dkpro.lab.storage.impl.PropertiesAdapter;
import de.tudarmstadt.ukp.dkpro.lab.task.Task;
import de.tudarmstadt.ukp.dkpro.lab.task.TaskContextMetadata;
import de.tudarmstadt.ukp.dkpro.tc.core.Constants;
import de.tudarmstadt.ukp.dkpro.tc.core.util.ReportUtils;
import de.tudarmstadt.ukp.dkpro.tc.ml.ExperimentCrossValidation;
import de.tudarmstadt.ukp.dkpro.tc.weka.task.WekaTestTask;

/**
 * Collects the final evaluation results in a train/test setting.
 * 
 * @author zesch
 * 
 */
public class WekaBatchTrainTestReport
    extends BatchReportBase
    implements Constants
{

    private static final List<String> discriminatorsToExclude = Arrays.asList(new String[] {
            "files_validation", "files_training" });

    @Override
    public void execute()
        throws Exception
    {
        StorageService store = getContext().getStorageService();

        FlexTable<String> table = FlexTable.forClass(String.class);

        Map<String, List<Double>> key2resultValues = new HashMap<String, List<Double>>();
        Map<List<String>, Double> confMatrixMap = new HashMap<List<String>, Double>();

        Properties outcomeIdProps = new Properties();

        for (TaskContextMetadata subcontext : getSubtasks()) {
            if (subcontext.getType().startsWith(WekaTestTask.class.getName())) {
                try {
                    outcomeIdProps.putAll(store.retrieveBinary(subcontext.getId(),
                            WekaOutcomeIDReport.ID_OUTCOME_KEY, new PropertiesAdapter()).getMap());
                }
                catch (Exception e) {
                    // silently ignore if this file was not generated
                }

                Map<String, String> discriminatorsMap = store.retrieveBinary(subcontext.getId(),
                        Task.DISCRIMINATORS_KEY, new PropertiesAdapter()).getMap();
                Map<String, String> resultMap = store.retrieveBinary(subcontext.getId(),
                        WekaTestTask.RESULTS_FILENAME, new PropertiesAdapter()).getMap();

                File confMatrix = store.getStorageFolder(subcontext.getId(),
                        CONFUSIONMATRIX_KEY);

                if (confMatrix.isFile()) {
                    confMatrixMap = ReportUtils
                            .updateAggregateMatrix(confMatrixMap, confMatrix);
                }
                else {
                    confMatrix.delete();
                }

                String key = getKey(discriminatorsMap);

                List<Double> results;
                if (key2resultValues.get(key) == null) {
                    results = new ArrayList<Double>();
                }
                else {
                    results = key2resultValues.get(key);
                }
                key2resultValues.put(key, results);

                Map<String, String> values = new HashMap<String, String>();
                Map<String, String> cleanedDiscriminatorsMap = new HashMap<String, String>();

                for (String disc : discriminatorsMap.keySet()) {
                    if (!ReportUtils.containsExcludePattern(disc, discriminatorsToExclude)) {
                        cleanedDiscriminatorsMap.put(disc, discriminatorsMap.get(disc));
                    }
                }
                values.putAll(cleanedDiscriminatorsMap);
                values.putAll(resultMap);

                table.addRow(subcontext.getLabel(), values);
            }
        }

        getContext().getLoggingService().message(getContextLabel(),
                ReportUtils.getPerformanceOverview(table));
        // Excel cannot cope with more than 255 columns
        if (table.getColumnIds().length <= 255) {
            getContext()
                    .storeBinary(EVAL_FILE_NAME + "_compact" + SUFFIX_EXCEL, table.getExcelWriter());
        }
        getContext().storeBinary(EVAL_FILE_NAME + "_compact" + SUFFIX_CSV, table.getCsvWriter());
        table.setCompact(false);
        // Excel cannot cope with more than 255 columns
        if (table.getColumnIds().length <= 255) {
            getContext().storeBinary(EVAL_FILE_NAME + SUFFIX_EXCEL, table.getExcelWriter());
        }
        getContext().storeBinary(EVAL_FILE_NAME + SUFFIX_CSV, table.getCsvWriter());

        // this report is reused in CV, and we only want to aggregate confusion matrices from folds
        // in CV, and an aggregated OutcomeIdReport
        if (getContext().getId().startsWith(ExperimentCrossValidation.class.getSimpleName())) {
            // no confusion matrix for regression
            if (confMatrixMap.size() > 0) {
                FlexTable<String> confMatrix = ReportUtils
                        .createOverallConfusionMatrix(confMatrixMap);
                getContext().storeBinary(CONFUSIONMATRIX_KEY, confMatrix.getCsvWriter());
            }
            if (outcomeIdProps.size() > 0)
                getContext().storeBinary(WekaOutcomeIDReport.ID_OUTCOME_KEY,
                        new PropertiesAdapter(outcomeIdProps));
        }

        // output the location of the batch evaluation folder
        // otherwise it might be hard for novice users to locate this
        File dummyFolder = store.getStorageFolder(getContext().getId(), "dummy");
        // TODO can we also do this without creating and deleting the dummy folder?
        getContext().getLoggingService().message(getContextLabel(),
                "Storing detailed results in:\n" + dummyFolder.getParent() + "\n");
        dummyFolder.delete();
    }

    private String getKey(Map<String, String> discriminatorsMap)
    {
        Set<String> sortedDiscriminators = new TreeSet<String>(discriminatorsMap.keySet());

        List<String> values = new ArrayList<String>();
        for (String discriminator : sortedDiscriminators) {
            values.add(discriminatorsMap.get(discriminator));
        }
        return StringUtils.join(values, "_");
    }
}