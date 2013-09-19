package de.tudarmstadt.ukp.dkpro.tc.testing;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReaderDescription

import org.apache.uima.analysis_engine.AnalysisEngineDescription
import org.apache.uima.resource.ResourceInitializationException

import weka.classifiers.bayes.NaiveBayes
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter
import de.tudarmstadt.ukp.dkpro.lab.Lab
import de.tudarmstadt.ukp.dkpro.lab.task.Dimension
import de.tudarmstadt.ukp.dkpro.lab.task.impl.BatchTask
import de.tudarmstadt.ukp.dkpro.lab.task.impl.BatchTask.ExecutionPolicy
import de.tudarmstadt.ukp.dkpro.tc.core.Constants
import de.tudarmstadt.ukp.dkpro.tc.demo.twentynewsgroups.io.TwentyNewsgroupsCorpusReader
import de.tudarmstadt.ukp.dkpro.tc.features.length.NrOfTokensFeatureExtractor
import de.tudarmstadt.ukp.dkpro.tc.features.ngram.NGramFeatureExtractor
import de.tudarmstadt.ukp.dkpro.tc.weka.report.BatchCrossValidationReport
import de.tudarmstadt.ukp.dkpro.tc.weka.report.BatchOutcomeIDReport
import de.tudarmstadt.ukp.dkpro.tc.weka.report.BatchTrainTestReport
import de.tudarmstadt.ukp.dkpro.tc.weka.report.TrainTestReport
import de.tudarmstadt.ukp.dkpro.tc.weka.task.BatchTaskCrossValidation
import de.tudarmstadt.ukp.dkpro.tc.weka.task.BatchTaskTrainTest
import de.tudarmstadt.ukp.dkpro.tc.weka.writer.WekaDataWriter

/**
 * Experiment setup used to test extreme configuration settings like empty feature extractors etc.
 * The experiment setup was adapted from the TwentyNewsgroups example.
 *
 * @author zesch
 * @author Oliver Ferschke
 */
public class ExtremeConfigurationSettingsExperiment implements Constants {

    def experimentName = "ExtremeConfigurationSettingsTest";

    // === PARAMETERS===========================================================

    def corpusFilePathTrain = "src/main/resources/data/train";
    def corpusFilePathTest  ="src/main/resources/data/test";
    def languageCode = "en";
    def numFolds = 2;
    def manyFolds = 10;

    // === DIMENSIONS===========================================================

    def dimReaderTest = Dimension.createBundle("readerTest", [
        readerTest: TwentyNewsgroupsCorpusReader.class,
        readerTestParams: [
            "sourceLocation",
            corpusFilePathTest,
            "language",
            languageCode,
            "patterns",
            TwentyNewsgroupsCorpusReader.INCLUDE_PREFIX + "*/*.txt"]
    ]);

    def dimReaderTrain = Dimension.createBundle("readerTrain", [
        readerTrain: TwentyNewsgroupsCorpusReader.class,
        readerTrainParams: [
            "sourceLocation",
            corpusFilePathTrain,
            "language",
            languageCode,
            "patterns",
            TwentyNewsgroupsCorpusReader.INCLUDE_PREFIX + "*/*.txt"]
    ]);

    def dimMultiLabel = Dimension.create(DIM_MULTI_LABEL, false);
    def dimDataWriter = Dimension.create(DIM_DATA_WRITER, WekaDataWriter.class.name);
    
    //UIMA parameters for FE configuration
    def dimPipelineParameters = Dimension.create(
    DIM_PIPELINE_PARAMS,
    [
        "TopK",
        "500",
        NGramFeatureExtractor.PARAM_NGRAM_MIN_N,
        1,
        NGramFeatureExtractor.PARAM_NGRAM_MAX_N,
        3
    ]
    );

    //UIMA parameters for FE configuration
    def dimPipelineParametersEmpty = Dimension.create("pipelineParameters", []);

    def dimClassificationArgs =
    Dimension.create(
    DIM_CLASSIFICATION_ARGS,
    [
        [NaiveBayes.class.name].toArray()
    ] as Object[]
    );

    def dimClassificationArgsEmpty =
    Dimension.create(DIM_CLASSIFICATION_ARGS, [] as Object[]);

    def dimFeatureSets = Dimension.create(
    DIM_FEATURE_SET,
    [
        [
            NrOfTokensFeatureExtractor.class.name,
            NGramFeatureExtractor.class.name
        ].toArray()
    ] as Object[]
    );

    def dimFeatureSetsEmpty = Dimension.create(DIM_FEATURE_SET, [] as Object[]);

    // === Test =========================================================

    public void runEmptyPipelineParameters() throws Exception
    {

        BatchTaskCrossValidation batchTask = [
            experimentName: experimentName + "-CV-Groovy",
            type: "Evaluation-"+ experimentName +"-CV-Groovy",
            aggregate: getPreprocessing(),
            innerReport: TrainTestReport.class,
            parameterSpace : [
                dimReaderTrain,
                dimMultiLabel,
                dimDataWriter,
                dimClassificationArgs,
                dimFeatureSets,
                dimPipelineParametersEmpty
            ],
            executionPolicy: ExecutionPolicy.RUN_AGAIN,
            reports:         [BatchCrossValidationReport],
            numFolds: numFolds];

        Lab.getInstance().run(batchTask);

        BatchTaskTrainTest batchTaskTrainTest = [
            experimentName: experimentName + "-TrainTest-Groovy",
            type: "Evaluation-"+ experimentName +"-TrainTest-Groovy",
            aggregate: getPreprocessing(),
            innerReport: TrainTestReport.class,
            parameterSpace : [
                dimReaderTrain,
                dimReaderTest,
                dimMultiLabel,
                dimDataWriter,
                dimClassificationArgs,
                dimFeatureSets,
                dimPipelineParametersEmpty
            ],
            executionPolicy: ExecutionPolicy.RUN_AGAIN,
            reports:         [
                BatchTrainTestReport,
                BatchOutcomeIDReport]
        ];

        // Run
        Lab.getInstance().run(batchTaskTrainTest);
    }

    public void runEmptyFeatureExtractorSet() throws Exception
    {

        BatchTaskCrossValidation batchTask = [
            experimentName: experimentName + "-CV-Groovy",
            type: "Evaluation-"+ experimentName +"-CV-Groovy",
            aggregate: getPreprocessing(),
            innerReport: TrainTestReport.class,
            parameterSpace : [
                dimReaderTrain,
                dimMultiLabel,
                dimDataWriter,
                dimClassificationArgs,
                dimFeatureSetsEmpty,
                dimPipelineParameters
            ],
            executionPolicy: ExecutionPolicy.RUN_AGAIN,
            reports:         [BatchCrossValidationReport],
            numFolds: numFolds];

        Lab.getInstance().run(batchTask);

        BatchTaskTrainTest batchTaskTrainTest = [
            experimentName: experimentName + "-TrainTest-Groovy",
            type: "Evaluation-"+ experimentName +"-TrainTest-Groovy",
            aggregate: getPreprocessing(),
            innerReport: TrainTestReport.class,
            parameterSpace : [
                dimReaderTrain,
                dimReaderTest,
                dimMultiLabel,
                dimDataWriter,
                dimClassificationArgs,
                dimFeatureSetsEmpty,
                dimPipelineParameters
            ],
            executionPolicy: ExecutionPolicy.RUN_AGAIN,
            reports:         [
                BatchTrainTestReport,
                BatchOutcomeIDReport]
        ];

        // Run
        Lab.getInstance().run(batchTaskTrainTest);
    }

    private AnalysisEngineDescription getPreprocessing()
    throws ResourceInitializationException
    {
        return createEngineDescription(
        createEngineDescription(BreakIteratorSegmenter.class),
        createEngineDescription(OpenNlpPosTagger.class)
        );
    }
}