package de.tudarmstadt.ukp.dkpro.tc.features.length;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.tc.api.exception.TextClassificationException;
import de.tudarmstadt.ukp.dkpro.tc.api.features.DocumentFeatureExtractor;
import de.tudarmstadt.ukp.dkpro.tc.api.features.Feature;
import de.tudarmstadt.ukp.dkpro.tc.api.features.FeatureExtractorResource_ImplBase;

/**
 * Extracts the number of characters in the document, per sentence, and per token
 */
@TypeCapability(inputs = { "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token" })
public class NrOfCharsDFE
    extends FeatureExtractorResource_ImplBase
    implements DocumentFeatureExtractor
{

    /**
     * Public name of the feature "number of characters"
     */
    public static final String FN_NR_OF_CHARS = "NrofChars";
    /**
     * Public name of the feature "number of characters per sentence"
     */
    public static final String FN_NR_OF_CHARS_PER_SENTENCE = "NrofCharsPerSentence";
    /**
     * Public name of the feature "number of characters per token"
     */
    public static final String FN_NR_OF_CHARS_PER_TOKEN = "NrofCharsPerToken";

    @Override
    public List<Feature> extract(JCas jcas)
        throws TextClassificationException
    {
        int nrOfChars = jcas.getDocumentText().length();
        int nrOfSentences = JCasUtil.select(jcas, Sentence.class).size();
        int nrOfTokens = JCasUtil.select(jcas, Token.class).size();

        List<Feature> featList = new ArrayList<Feature>();
        featList.add(new Feature(FN_NR_OF_CHARS, nrOfChars));

        double charPerSentence = 0.0;
        if (nrOfSentences > 0) {
            charPerSentence = (double) nrOfChars / nrOfSentences;
        }
        featList.add(new Feature(FN_NR_OF_CHARS_PER_SENTENCE, charPerSentence));

        double charPerToken = 0.0;
        if (nrOfTokens > 0) {
            charPerToken = (double) nrOfChars / nrOfTokens;
        }
        featList.add(new Feature(FN_NR_OF_CHARS_PER_TOKEN, charPerToken));

        return featList;
    }
}