package de.tudarmstadt.ukp.dkpro.tc.experiments.reuters.io;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.descriptor.ConfigurationParameter;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ResourceUtils;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import de.tudarmstadt.ukp.dkpro.tc.type.TextClassificationOutcome;

public class ReutersCorpusReader
    extends TextReader
{

    /**
     * Path to the file containing the gold standard labels.
     */
    public static final String PARAM_GOLD_LABEL_FILE = "GoldLabelFile";
    @ConfigurationParameter(name = PARAM_GOLD_LABEL_FILE, mandatory = true)
    private String goldLabelFile;

    private Map<String, List<String>> goldLabelMap;

    @Override
    public void initialize(UimaContext context)
        throws ResourceInitializationException
    {
        super.initialize(context);

        goldLabelMap = new HashMap<String, List<String>>();

        try {
            URL resourceUrl = ResourceUtils.resolveLocation(goldLabelFile, this, context);

            for (String line : FileUtils.readLines(new File(resourceUrl.toURI()))) {
                String[] parts = line.split(" ");

                if (parts.length < 2) {
                    throw new IOException("Wrong file format in line: " + line);
                }
                String fileId = parts[0].split("/")[1];

                List<String> labels = new ArrayList<String>();
                for (int i=1; i<parts.length; i++) {
                    labels.add(parts[i]);
                }

                goldLabelMap.put(fileId, labels);
            }
        }
        catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
        catch (URISyntaxException ex) {
            throw new ResourceInitializationException(ex);
        }
    }

    @Override
    public void getNext(CAS aCAS)
        throws IOException, CollectionException
    {
        super.getNext(aCAS);

        JCas jcas;
        try {
            jcas = aCAS.getJCas();
        }
        catch (CASException e) {
            throw new CollectionException();
        }

        DocumentMetaData dmd = DocumentMetaData.get(aCAS);
        String titleWithoutExtension = FilenameUtils.removeExtension(dmd.getDocumentTitle());

        if (!goldLabelMap.containsKey(titleWithoutExtension)) {
            throw new IOException("No gold label for document: " + dmd.getDocumentTitle());
        }

        for (String label : goldLabelMap.get(titleWithoutExtension)) {
            TextClassificationOutcome outcome = new TextClassificationOutcome(jcas);
            outcome.setOutcome(label);
            outcome.addToIndexes();
        }
    }
}
