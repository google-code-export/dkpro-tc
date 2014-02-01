package de.tudarmstadt.ukp.dkpro.tc.features.pair.core.ngram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.PriorityQueue;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import de.tudarmstadt.ukp.dkpro.core.api.frequency.util.FrequencyDistribution;
import de.tudarmstadt.ukp.dkpro.tc.api.features.Feature;
import de.tudarmstadt.ukp.dkpro.tc.api.features.util.FeatureUtil;
import de.tudarmstadt.ukp.dkpro.tc.core.io.AbstractPairReader;
import de.tudarmstadt.ukp.dkpro.tc.exception.TextClassificationException;
import de.tudarmstadt.ukp.dkpro.tc.features.ngram.TermFreqQueue;
import de.tudarmstadt.ukp.dkpro.tc.features.ngram.TermFreqTuple;
import de.tudarmstadt.ukp.dkpro.tc.type.TextClassificationUnit;

/**
 * Pair ngram feature extractor for document pair classification.  
 * Can be used to extract ngrams from one or both documents in the pair, and parameters
 * for each document (view 1's, view 2's) can be set separately, or both documents can 
 * be treated together as one extended document.
 * <br />
 * Note that ngram features created by this class are each from a single document, i.e., not
 * combinations of ngrams from the pair of documents.  To make combinations of ngrams
 * across both documents, please use {@link CombinedNGramPairFeatureExtractor}.
 * 
 * @author Emily Jamison
 *
 */
public class LuceneNGramPairFeatureExtractor
	extends LucenePairFeatureExtractorBase
{
    /**
     * Minimum size n of ngrams from View 1's.
     */
    public static final String PARAM_NGRAM_MIN_N_VIEW1 = "pairNgramMinNView1";
    @ConfigurationParameter(name = PARAM_NGRAM_MIN_N_VIEW1, mandatory = true, defaultValue = "1")
    protected int ngramMinN1;
    /**
     * Minimum size n of ngrams from View 2's.
     */
    public static final String PARAM_NGRAM_MIN_N_VIEW2 = "pairNgramMinNView2";
    @ConfigurationParameter(name = PARAM_NGRAM_MIN_N_VIEW2, mandatory = true, defaultValue = "1")
    protected int ngramMinN2;
    /**
     * Minimum size n of ngrams from any view.
     */
    public static final String PARAM_NGRAM_MIN_N_ALL = "pairNgramMinNAll";
    @ConfigurationParameter(name = PARAM_NGRAM_MIN_N_ALL, mandatory = true, defaultValue = "1")
    protected int ngramMinNAll;
    /**
     * Maximum size n of ngrams from View 1's.
     */
    public static final String PARAM_NGRAM_MAX_N_VIEW1 = "pairNgramMaxNView1";
    @ConfigurationParameter(name = PARAM_NGRAM_MAX_N_VIEW1, mandatory = true, defaultValue = "3")
    protected int ngramMaxN1;
    /**
     * Maximum size n of ngrams from View 2's.
     */
    public static final String PARAM_NGRAM_MAX_N_VIEW2 = "pairNgramMaxNView2";
    @ConfigurationParameter(name = PARAM_NGRAM_MAX_N_VIEW2, mandatory = true, defaultValue = "3")
    protected int ngramMaxN2;
    /**
     * Maximum size n of ngrams from any view.
     */
    public static final String PARAM_NGRAM_MAX_N_ALL = "pairNgramMaxNAll";
    @ConfigurationParameter(name = PARAM_NGRAM_MAX_N_ALL, mandatory = true, defaultValue = "3")
    protected int ngramMaxNAll;
    /**
     * Use this number of most frequent ngrams from View 1's.
     */
    public static final String PARAM_NGRAM_USE_TOP_K_VIEW1 = "pairNgramUseTopK1";
    @ConfigurationParameter(name = PARAM_NGRAM_USE_TOP_K_VIEW1, mandatory = true, defaultValue = "500")
    protected int ngramUseTopK1;
    /**
     * Use this number of most frequent ngrams from View 2's.
     */
    public static final String PARAM_NGRAM_USE_TOP_K_VIEW2 = "pairNgramUseTopK2";
    @ConfigurationParameter(name = PARAM_NGRAM_USE_TOP_K_VIEW2, mandatory = true, defaultValue = "500")
    protected int ngramUseTopK2;
    /**
     * Use this number of most frequent ngrams originating from any view.
     */
    public static final String PARAM_NGRAM_USE_TOP_K_ALL = "pairNgramUseTopKAll";
    @ConfigurationParameter(name = PARAM_NGRAM_USE_TOP_K_ALL, mandatory = true, defaultValue = "500")
    protected int ngramUseTopKAll;
    /**
     * All ngrams from View 1's with a frequency above this value will be used.
     */
    public static final String PARAM_NGRAM_FREQ_THRESHOLD_VIEW1 = "pairNgramFreqThreshold1";
    @ConfigurationParameter(name = PARAM_NGRAM_FREQ_THRESHOLD_VIEW1, mandatory = true, defaultValue = "0.01")
    protected float ngramFreqThreshold1;
    /**
     * All ngrams from View 2's with a frequency above this value will be used.
     */
    public static final String PARAM_NGRAM_FREQ_THRESHOLD_VIEW2 = "pairNgramFreqThreshold2";
    @ConfigurationParameter(name = PARAM_NGRAM_FREQ_THRESHOLD_VIEW2, mandatory = true, defaultValue = "0.01")
    protected float ngramFreqThreshold2;
    /**
     * All ngrams originating from any view with a frequency above this value will be used.
     */
    public static final String PARAM_NGRAM_FREQ_THRESHOLD_ALL = "pairNgramFreqThresholdAll";
    @ConfigurationParameter(name = PARAM_NGRAM_FREQ_THRESHOLD_ALL, mandatory = true, defaultValue = "0.01")
    protected float ngramFreqThresholdAll;
    /**
     * Each ngram from View 1 documents added to the document pair instance as a feature.  
     * E.g. Feature: view1NG_Dear
     */
    public static final String PARAM_USE_VIEW1_NGRAMS_AS_FEATURES = "useView1NgramsAsFeatures";
    @ConfigurationParameter(name = PARAM_USE_VIEW1_NGRAMS_AS_FEATURES, mandatory = false, defaultValue = "false")
    protected boolean useView1NgramsAsFeatures;
    /**
     * Each ngram from View 1 documents added to the document pair instance as a feature.  
     * E.g. Feature: view2NG_Dear
     */
    public static final String PARAM_USE_VIEW2_NGRAMS_AS_FEATURES = "useView2NgramsAsFeatures";
    @ConfigurationParameter(name = PARAM_USE_VIEW2_NGRAMS_AS_FEATURES, mandatory = false, defaultValue = "false")
    protected boolean useView2NgramsAsFeatures;
    /**
     * All qualifying ngrams from anywhere in either document are used as features.  Feature 
     * does not specify which view the ngram came from.
     * E.g. Feature: allNG_Dear
     */
    public static final String PARAM_USE_VIEWBLIND_NGRAMS_AS_FEATURES = "useViewBlindNgramsAsFeatures";
    @ConfigurationParameter(name = PARAM_USE_VIEWBLIND_NGRAMS_AS_FEATURES, mandatory = false, defaultValue = "false")
    protected boolean useViewBlindNgramsAsFeatures;
    /**
     * This option collects a FrequencyDistribution of ngrams across both documents of all pairs, 
     * but when writing features, the view where a particular ngram is found is recorded with the ngram.
     * For example, using a {@link #PARAM_NGRAM_USE_TOP_K_ALL} value of 500, 400 of the ngrams in the 
     * top 500 might happen to be from View 2's; and whenever an ngram from the 500 is seen in any 
     * document, view 1 or 2, the document's view is recorded.<br />
     * E.g., Feature: view2allNG_Dear<br />
     * In order to use this option, {@link #PARAM_USE_VIEWBLIND_NGRAMS_AS_FEATURES} must also be set to true.
     */
    public static final String PARAM_MARK_VIEWBLIND_NGRAMS_WITH_LOCAL_VIEW = "markViewBlindNgramsWithLocalView";
    @ConfigurationParameter(name = PARAM_MARK_VIEWBLIND_NGRAMS_WITH_LOCAL_VIEW, mandatory = false, defaultValue = "false")
    protected boolean markViewBlindNgramsWithLocalView;
    /**
     * List of words <b>not</b> to be included as ngrams.  
     */
    public static final String PARAM_NGRAM_STOPWORDS_FILE = "pairNgramStopwordsFile";
    @ConfigurationParameter(name = PARAM_NGRAM_STOPWORDS_FILE, mandatory = false)
    protected String ngramStopwordsFile;
    /**
     * If true, ngrams and stopwords will be lower-cased.
     */
    public static final String PARAM_NGRAM_LOWER_CASE = "pairNgramLowerCase";
    @ConfigurationParameter(name = PARAM_NGRAM_LOWER_CASE, mandatory = true, defaultValue = "true")
    protected boolean ngramLowerCase;
    /**
     * Minimum token length for a token to be included as an ngram.
     */
    public static final String PARAM_NGRAM_MIN_TOKEN_LENGTH_THRESHOLD = "pairNgramMinTokenLengthThreshold";
    @ConfigurationParameter(name = PARAM_NGRAM_MIN_TOKEN_LENGTH_THRESHOLD, mandatory = true, defaultValue = "1")
    protected int ngramMinTokenLengthThreshold;
    
    // These are only public so the MetaCollector can see them
    public static final String LUCENE_NGRAM_FIELD = "ngram";
    public static final String LUCENE_NGRAM_FIELD1 = "ngram1";
    public static final String LUCENE_NGRAM_FIELD2 = "ngram2";

    protected Set<String> stopwords;
    protected FrequencyDistribution<String> topKSetView1;
    protected FrequencyDistribution<String> topKSetView2;
    protected FrequencyDistribution<String> topKSetAll;
    protected String prefix;

    @Override
    public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
        throws ResourceInitializationException
    {
        if (!super.initialize(aSpecifier, aAdditionalParams)) {
            return false;
        }

        try {
            stopwords = FeatureUtil.getStopwords(ngramStopwordsFile, ngramLowerCase);
        }
        catch (IOException e) {
            throw new ResourceInitializationException(e);
        }

        topKSetAll = getTopNgrams();
        topKSetView1 = getTopNgramsView1();
        topKSetView2 = getTopNgramsView2();

        return true;
    }
    
    @Override
    public List<Feature> extract(JCas jcas, TextClassificationUnit classificationUnit)
        throws TextClassificationException
    {   
        FrequencyDistribution<String> view1Ngrams = getViewNgrams(
        		AbstractPairReader.PART_ONE, jcas, classificationUnit);
        FrequencyDistribution<String> view2Ngrams = getViewNgrams(
        		AbstractPairReader.PART_TWO, jcas, classificationUnit);
        FrequencyDistribution<String> allNgrams = getViewNgrams(
                AbstractPairReader.INITIAL_VIEW, jcas, classificationUnit);
        
        List<Feature> features = new ArrayList<Feature>();
        if(useView1NgramsAsFeatures){
    		prefix = "view1NG";
        	features = addToFeatureArray(view1Ngrams, topKSetView1, features);
        }
        if(useView2NgramsAsFeatures){
    		prefix = "view2NG";
        	features = addToFeatureArray(view2Ngrams, topKSetView2, features);
        }
        if(useViewBlindNgramsAsFeatures && !markViewBlindNgramsWithLocalView){
    		prefix = "allNG";
        	features = addToFeatureArray(allNgrams, topKSetAll, features);
        }
        if(useViewBlindNgramsAsFeatures && markViewBlindNgramsWithLocalView){
            prefix = "view1allNG";
            features = addToFeatureArray(view1Ngrams, topKSetAll, features);
            prefix = "view2allNG";
            features = addToFeatureArray(view2Ngrams, topKSetAll, features);
        }
        
        return features;
    }

	protected List<Feature> addToFeatureArray(FrequencyDistribution<String> viewNgrams, FrequencyDistribution<String> topKSet,
			List<Feature> features)
	{
		for(String ngram: topKSet.getKeys()){
			if(viewNgrams.contains(ngram)){
				features.add(new Feature(ComboUtils.combo(prefix, ngram), 1));
			}else{
				features.add(new Feature(ComboUtils.combo(prefix, ngram), 0));
			}
		}
		return features;
	}
    
    protected FrequencyDistribution<String> getTopNgrams()
        throws ResourceInitializationException
    {       
        return getTopNgrams(ngramUseTopKAll, LUCENE_NGRAM_FIELD);
    }
    
    protected FrequencyDistribution<String> getTopNgramsView1()
        throws ResourceInitializationException
    {
        return getTopNgrams(ngramUseTopK1, LUCENE_NGRAM_FIELD1);
    }

    protected FrequencyDistribution<String> getTopNgramsView2()
        throws ResourceInitializationException
    {
        return getTopNgrams(ngramUseTopK2, LUCENE_NGRAM_FIELD2);
    }
    
    private FrequencyDistribution<String> getTopNgrams(int topNgramThreshold, String fieldName)
        throws ResourceInitializationException
    {       

    	FrequencyDistribution<String> topNGrams = new FrequencyDistribution<String>();
        
        PriorityQueue<TermFreqTuple> topN = new TermFreqQueue(topNgramThreshold);

        IndexReader reader;
        try {
            reader = DirectoryReader.open(FSDirectory.open(luceneDir));
            Fields fields = MultiFields.getFields(reader);
            if (fields != null) {
                Terms terms = fields.terms(fieldName);
                if (terms != null) {
                    TermsEnum termsEnum = terms.iterator(null);
                    BytesRef text = null;
                    while ((text = termsEnum.next()) != null) {
                        String term = text.utf8ToString();
                        long freq = termsEnum.totalTermFreq();
                        topN.insertWithOverflow(new TermFreqTuple(term, freq));
                    }
                }
            }
        }
        catch (Exception e) {
            throw new ResourceInitializationException(e);
        }
        
        for (int i=0; i < topN.size(); i++) {
            TermFreqTuple tuple = topN.pop();
//                System.out.println(tuple.getTerm() + " - " + tuple.getFreq());
            topNGrams.addSample(tuple.getTerm(), tuple.getFreq());
        }
        
        return topNGrams;
    }
    
    protected FrequencyDistribution<String> getViewNgrams(String name, JCas jcas, 
    		TextClassificationUnit classificationUnit)
    		throws TextClassificationException{
    	if(name.equals(AbstractPairReader.PART_ONE)){
    		return ComboUtils.getViewNgrams(jcas, name, classificationUnit, ngramLowerCase, ngramMinN1, ngramMaxN1, stopwords);
    	}else if(name.equals(AbstractPairReader.PART_TWO)){
    		return ComboUtils.getViewNgrams(jcas, name, classificationUnit, ngramLowerCase, ngramMinN2, ngramMaxN2, stopwords);
    	}else{
    		return ComboUtils.getViewNgrams(jcas, name, classificationUnit, ngramLowerCase, ngramMinN2, ngramMaxN2, stopwords);
    	}
    	
    }
}