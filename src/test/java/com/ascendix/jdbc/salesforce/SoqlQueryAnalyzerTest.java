package com.ascendix.jdbc.salesforce;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ascendix.jdbc.salesforce.SoqlQueryAnalyzer.FieldDef;
import com.sforce.soap.partner.DescribeSObjectResult;
import com.thoughtworks.xstream.XStream;

public class SoqlQueryAnalyzerTest {

    @Test
    public void testGetFieldNames_SimpleQuery() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer(" select Id ,Name \r\nfrom Account\r\n where something = 'nothing' ", n -> this.describeSObject(n));
	List<String> expecteds = Arrays.asList("Id", "Name");
	List<String> actuals = listFlatFieldNames(analyzer);
	
	assertEquals(expecteds, actuals);
    }

    private List<String> listFlatFieldNames(SoqlQueryAnalyzer analyzer) {
	return listFlatFieldDefinitions(analyzer.getFieldDefinitions()).stream()
		.map(SoqlQueryAnalyzer.FieldDef::getName)
		.collect(Collectors.toList());
    }

    @Test
    public void testGetFieldNames_SelectWithReferences() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer(" select Id , Account.Name \r\nfrom Contact\r\n where something = 'nothing' ", n -> this.describeSObject(n));
	List<String> expecteds = Arrays.asList("Id", "Name");
	List<String> actuals = listFlatFieldNames(analyzer);
	
	assertEquals(expecteds, actuals);
    }

    @Test
    public void testGetFieldNames_SelectWithAggregateAliased() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer(" select Id , Account.Name, count(id) aggrAlias1\r\nfrom Contact\r\n where something = 'nothing' ", n -> this.describeSObject(n));
	List<String> expecteds = Arrays.asList("Id", "Name", "aggrAlias1");
	List<String> actuals = listFlatFieldNames(analyzer);
	
	assertEquals(expecteds, actuals);
    }

    @Test
    public void testGetFieldNames_SelectWithAggregate() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer(" select Id , Account.Name, count(id)\r\nfrom Contact\r\n where something = 'nothing' ", n -> this.describeSObject(n));
	List<String> expecteds = Arrays.asList("Id", "Name", "count");
	List<String> actuals = listFlatFieldNames(analyzer);
	
	assertEquals(expecteds, actuals);
    }

    @Test
    public void testGetFromObjectName() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer(" select Id , Account.Name \r\nfrom Contact\r\n where something = 'nothing' ", n -> this.describeSObject(n));
	String expected = "Contact";
	String actual = analyzer.getFromObjectName();
	
	assertEquals(expected, actual);
    }
    
    private List<FieldDef> listFlatFieldDefinitions(List<?> fieldDefs) {
	return (List<FieldDef>) fieldDefs.stream()
		.flatMap(def -> def instanceof List 
			? ((List) def).stream() 
			: Arrays.asList(def).stream())
		.collect(Collectors.toList());
    }

    @Test
    public void testGetSimpleFieldDefinitions() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT Id, Name FROM Account", n -> this.describeSObject(n));

	List<FieldDef> actuals = listFlatFieldDefinitions(analyzer.getFieldDefinitions());
	assertEquals(2, actuals.size());
	assertEquals("Id", actuals.get(0).getName());
	assertEquals("id", actuals.get(0).getType());

	assertEquals("Name", actuals.get(1).getName());
	assertEquals("string", actuals.get(1).getType());
    }
    
    @Test
    public void testGetReferenceFieldDefinitions() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT Account.Name FROM Contact", n -> this.describeSObject(n));

	List<FieldDef> actuals = listFlatFieldDefinitions(analyzer.getFieldDefinitions());
	assertEquals(1, actuals.size());
	assertEquals("Name", actuals.get(0).getName());
	assertEquals("string", actuals.get(0).getType());
    }
    
    @Test
    public void testGetAggregateFieldDefinition() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT MIN(Name) FROM Contact", n -> this.describeSObject(n));

	List<FieldDef> actuals = listFlatFieldDefinitions(analyzer.getFieldDefinitions());
	assertEquals(1, actuals.size());
	assertEquals("MIN", actuals.get(0).getName());
	assertEquals("string", actuals.get(0).getType());
    }
    
    @Test
    public void testGetAggregateFieldDefinitionWithoutParameter() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT Count() FROM Contact", n -> this.describeSObject(n));

	List<FieldDef> actuals = listFlatFieldDefinitions(analyzer.getFieldDefinitions());
	assertEquals(1, actuals.size());
	assertEquals("Count", actuals.get(0).getName());
	assertEquals("int", actuals.get(0).getType());
    }
    
    @Test
    public void testGetSimpleFieldWithQualifier() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT Contact.Id FROM Contact", n -> this.describeSObject(n));

	List<FieldDef> actuals = listFlatFieldDefinitions(analyzer.getFieldDefinitions());
	assertEquals(1, actuals.size());
	assertEquals("Id", actuals.get(0).getName());
	assertEquals("id", actuals.get(0).getType());
    }
    
    @Test
    public void testGetNamedAggregateFieldDefinitions() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT count(Name) nameCount FROM Account", n -> this.describeSObject(n));

	List<FieldDef> actuals = listFlatFieldDefinitions(analyzer.getFieldDefinitions());
	
	assertEquals(1, actuals.size());
	assertEquals("nameCount", actuals.get(0).getName());
	assertEquals("int", actuals.get(0).getType());
    }
    
    private DescribeSObjectResult describeSObject(String sObjectType) {
	try {
	    String xml = new String(Files.readAllBytes(Paths.get("src/test/resources/" + sObjectType + "_desription.xml")));
	    XStream xstream = new XStream();
	    return (DescribeSObjectResult) xstream.fromXML(xml);
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
    }
    
    @Test
    public void testFetchFieldDefinitions_WithIncludedSeslect() {
	SoqlQueryAnalyzer analyzer = new SoqlQueryAnalyzer("SELECT Name, (SELECT Id, max(LastName) maxLastName FROM Contacts), Id FROM Account", n -> this.describeSObject(n));
	
	List actuals = analyzer.getFieldDefinitions();
	
	assertEquals(3, actuals.size());
	FieldDef fieldDef = (FieldDef) actuals.get(0);
	assertEquals("Name", fieldDef.getName());
	assertEquals("string", fieldDef.getType());
	
	List suqueryDef = (List) actuals.get(1);
	fieldDef = (FieldDef) suqueryDef.get(0);
	assertEquals("Id", fieldDef.getName());
	assertEquals("id", fieldDef.getType());
	
	fieldDef = (FieldDef) suqueryDef.get(1);
	assertEquals("maxLastName", fieldDef.getName());
	assertEquals("string", fieldDef.getType());
	
	fieldDef = (FieldDef) actuals.get(2);
	assertEquals("Id", fieldDef.getName());
	assertEquals("id",fieldDef.getType());
    }
    
}
