package datawave.microservice.dictionary.edge;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import datawave.accumulo.inmemory.InMemoryInstance;
import datawave.data.ColumnFamilyConstants;
import datawave.metadata.protobuf.EdgeMetadata.MetadataValue;
import datawave.webservice.results.edgedictionary.DefaultEdgeDictionary;
import datawave.webservice.results.edgedictionary.EventField;
import datawave.webservice.results.edgedictionary.DefaultMetadata;
import datawave.webservice.results.edgedictionary.MetadataBase;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.io.Text;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class DefaultDatawaveEdgeDictionaryImplTest {
    
    public static final String EDGE_TYPE = "TYPE";
    public static final String SOURCE_RELATIONSHIP = "OWNER";
    public static final String SINK_RELATIONSHIP = "PET";
    public static final String ATTRIBUTE3 = "abc123";
    public static final String SINK_ATTRIBUTE1 = "EYES";
    public static final String[] SOURCE_ATTRIBUTE1 = new String[] {"P", "Q", "R", "S"};
    public static final String ATTRIBUTE2 = "blue";
    public static final String SOURCE_FIELD = "M_E_123";
    public static final String SINK_FIELD = "M_E_123";
    public static final String EARLY_DATE_FIELD = "20130318";
    
    private static final List<Key> EDGE_KEYS = createEdgeKeys();
    private static final Value EDGE_VALUE = createEdgeValue();
    private static final Collection<DefaultMetadata> METADATA = createMetadata();
    
    private static InMemoryInstance instance = new InMemoryInstance();
    
    SetMultimap<Key,Value> edgeMetadataRows;
    Method transformResultsMethod;
    
    @Autowired
    private DatawaveEdgeDictionary<DefaultEdgeDictionary,DefaultMetadata> impl;
    
    @Before
    public void setUp() {
        edgeMetadataRows = HashMultimap.create();
        for (Map.Entry<Key,Value> entry : permuteKeysAndEdges(EDGE_KEYS, EDGE_VALUE)) {
            edgeMetadataRows.put(entry.getKey(), entry.getValue());
        }
        transformResultsMethod = getPrivateMethod("transformResults");
    }
    
    private static List<Map.Entry<Key,Value>> permuteKeysAndEdges(List<Key> edgeKeys, Value... edgeValue) {
        ArrayList<Map.Entry<Key,Value>> list = new ArrayList<>();
        for (Key key : edgeKeys) {
            for (Value value : edgeValue) {
                list.add(new HashMap.SimpleEntry<>(key, value));
            }
        }
        return list;
    }
    
    @Test
    public void testNoOp() throws InvocationTargetException, IllegalAccessException {
        if (null == transformResultsMethod)
            fail();
        DefaultEdgeDictionary dictionary = (DefaultEdgeDictionary) transformResultsMethod.invoke(impl, HashMultimap.create());
        Assert.assertEquals("Should be empty", dictionary.getTotalResults(), 0L);
    }
    
    @Test
    public void testWorked() throws InvocationTargetException, IllegalAccessException {
        if (null == transformResultsMethod)
            fail();
        Assert.assertEquals("data to be inserted contains as many rows as keys", edgeMetadataRows.keySet().size(), EDGE_KEYS.size());
        DefaultEdgeDictionary dictionary = (DefaultEdgeDictionary) transformResultsMethod.invoke(impl, edgeMetadataRows);
        Assert.assertEquals("Dictionary should now have some entries", dictionary.getTotalResults(), EDGE_KEYS.size());
        Assert.assertTrue("METADATA not in list.  returned list: " + dictionary.getMetadataList() + " expected: " + METADATA,
                        dictionary.getMetadataList().containsAll(METADATA));
    }
    
    @Test
    public void earliestDateFound() throws InvocationTargetException, IllegalAccessException {
        if (null == transformResultsMethod)
            fail();
        
        DefaultEdgeDictionary dictionary = (DefaultEdgeDictionary) transformResultsMethod.invoke(impl, edgeMetadataRows);
        
        List<? extends MetadataBase<DefaultMetadata>> metadata = dictionary.getMetadataList();
        
        // Make sure that all Metadata in EdgeDictionary have the start date set to the EARLY_DATE_FIELD
        for (MetadataBase<DefaultMetadata> meta : metadata) {
            Assert.assertEquals("Incorrect start date. Expected: " + EARLY_DATE_FIELD + " Found: " + meta.getStartDate(), EARLY_DATE_FIELD,
                            meta.getStartDate());
            
        }
    }
    
    private Method getPrivateMethod(String methodName) {
        Class clas = DefaultDatawaveEdgeDictionaryImpl.class;
        for (Method method : clas.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
    
    private static Value createEdgeValue() {
        // Extra Metadata values with different dates to ensure that the earliest date is chosen to be the start date
        // of collection. Early Date = 20130318
        MetadataValue.Metadata value1 = MetadataValue.Metadata.newBuilder().setSource(SOURCE_FIELD).setSink(SINK_FIELD).setDate("20130415").build();
        MetadataValue.Metadata value2 = MetadataValue.Metadata.newBuilder().setSource(SOURCE_FIELD).setSink(SINK_FIELD).setDate("20130320").build();
        MetadataValue.Metadata value3 = MetadataValue.Metadata.newBuilder().setSource(SOURCE_FIELD).setSink(SINK_FIELD).setDate(EARLY_DATE_FIELD).build();
        MetadataValue.Metadata value4 = MetadataValue.Metadata.newBuilder().setSource(SOURCE_FIELD).setSink(SINK_FIELD).setDate("20140314").build();
        MetadataValue.Metadata value5 = MetadataValue.Metadata.newBuilder().setSource(SOURCE_FIELD).setSink(SINK_FIELD).setDate("20130319").build();
        
        MetadataValue.Builder valueBuilder = MetadataValue.newBuilder();
        valueBuilder.addAllMetadata(Collections.singletonList(value1));
        valueBuilder.addAllMetadata(Collections.singletonList(value2));
        valueBuilder.addAllMetadata(Collections.singletonList(value3));
        valueBuilder.addAllMetadata(Collections.singletonList(value4));
        valueBuilder.addAllMetadata(Collections.singletonList(value5));
        
        return new Value(valueBuilder.build().toByteArray());
    }
    
    private static ArrayList<Key> createEdgeKeys() {
        ArrayList<Key> result = new ArrayList<>();
        for (String currAttribute1 : SOURCE_ATTRIBUTE1) {
            result.add(generateKeyForEdgeMetadata(currAttribute1));
        }
        return result;
    }
    
    private static Key generateKeyForEdgeMetadata(String source_attribute1) {
        Text row = new Text(EDGE_TYPE + DatawaveEdgeDictionary.COL_SEPARATOR + SOURCE_RELATIONSHIP + "-" + SINK_RELATIONSHIP);
        Text colf = ColumnFamilyConstants.COLF_EDGE;
        Text colq = new Text(source_attribute1 + "-" + SINK_ATTRIBUTE1);
        Text colv = new Text("");
        
        return new Key(row, colf, colq, colv, Long.MAX_VALUE);
    }
    
    private static Collection<DefaultMetadata> createMetadata() {
        Collection<DefaultMetadata> metadataList = new LinkedList<>();
        for (String source_attribute1 : SOURCE_ATTRIBUTE1) {
            DefaultMetadata metadata = new DefaultMetadata();
            metadata.setEdgeType(EDGE_TYPE);
            metadata.setEdgeRelationship(SOURCE_RELATIONSHIP + "-" + SINK_RELATIONSHIP);
            metadata.setEdgeAttribute1Source(source_attribute1 + "-" + SINK_ATTRIBUTE1);
            EventField field = new EventField();
            field.setSourceField(SOURCE_FIELD);
            field.setSinkField(SINK_FIELD);
            
            metadata.setEventFields(Collections.singletonList(field));
            metadata.setStartDate(EARLY_DATE_FIELD);
            metadataList.add(metadata);
        }
        return metadataList;
    }
    
    @ComponentScan(basePackages = "datawave.microservice")
    @Configuration
    public static class DefaultDatawaveEdgeDictionaryImplTestConfiguration {
        @Bean
        @Qualifier("warehouse")
        public Connector warehouseConnector() throws AccumuloSecurityException, AccumuloException {
            return instance.getConnector("root", new PasswordToken(""));
        }
    }
}
