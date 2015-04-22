package ca.uhn.fhir.parser;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.core.StringContains;
import org.hamcrest.text.StringContainsInOrder;
import org.hl7.fhir.instance.model.IBaseResource;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.Tag;
import ca.uhn.fhir.model.api.TagList;
import ca.uhn.fhir.model.base.composite.BaseCodingDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.ContainedDt;
import ca.uhn.fhir.model.dstu2.composite.DurationDt;
import ca.uhn.fhir.model.dstu2.composite.ElementDefinitionDt;
import ca.uhn.fhir.model.dstu2.composite.ElementDefinitionDt.Binding;
import ca.uhn.fhir.model.dstu2.composite.HumanNameDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.AllergyIntolerance;
import ca.uhn.fhir.model.dstu2.resource.Binary;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.resource.DataElement;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Medication;
import ca.uhn.fhir.model.dstu2.resource.MedicationPrescription;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Organization;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.AddressUseEnum;
import ca.uhn.fhir.model.dstu2.valueset.DocumentReferenceStatusEnum;
import ca.uhn.fhir.model.dstu2.valueset.IdentifierUseEnum;
import ca.uhn.fhir.model.primitive.DateDt;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.model.primitive.StringDt;

public class XmlParserDstu2Test {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(XmlParserDstu2Test.class);
	private static final FhirContext ourCtx = FhirContext.forDstu2();

	@BeforeClass
	public static void beforeClass() {
		XMLUnit.setIgnoreAttributeOrder(true);
		XMLUnit.setIgnoreComments(true);
		XMLUnit.setIgnoreWhitespace(true);
	}

	@Test
	public void testEncodeExtensionWithResourceContent() {
		IParser parser = ourCtx.newXmlParser();

		Patient patient = new Patient();
		patient.addAddress().setUse(AddressUseEnum.HOME);
		patient.addUndeclaredExtension(false, "urn:foo", new ResourceReferenceDt("Organization/123"));

		String val = parser.encodeResourceToString(patient);
		ourLog.info(val);
		assertThat(val, StringContains.containsString("<extension url=\"urn:foo\"><valueReference><reference value=\"Organization/123\"/></valueReference></extension>"));

		Patient actual = parser.parseResource(Patient.class, val);
		assertEquals(AddressUseEnum.HOME, patient.getAddress().get(0).getUse());
		List<ExtensionDt> ext = actual.getUndeclaredExtensions();
		assertEquals(1, ext.size());
		ResourceReferenceDt ref = (ResourceReferenceDt) ext.get(0).getValue();
		assertEquals("Organization/123", ref.getReference().getValue());

	}

	@Test
	public void testContainedResourceInExtensionUndeclared() {
		Patient p = new Patient();
		p.addName().addFamily("PATIENT");
		
		Organization o = new Organization();
		o.setName("ORG");
		p.addUndeclaredExtension(new ExtensionDt(false, "urn:foo", new ResourceReferenceDt(o)));
		
		String str = ourCtx.newXmlParser().encodeResourceToString(p);
		ourLog.info(str);
		
		p = ourCtx.newXmlParser().parseResource(Patient.class, str);
		assertEquals("PATIENT", p.getName().get(0).getFamily().get(0).getValue());
		
		List<ExtensionDt> exts = p.getUndeclaredExtensionsByUrl("urn:foo");
		assertEquals(1, exts.size());
		ResourceReferenceDt rr = (ResourceReferenceDt)exts.get(0).getValue();
		o = (Organization) rr.getResource();
		assertEquals("ORG", o.getName());
	}
	
	@Test
	public void testEncodeAndParseExtensionOnResourceReference() {
		DataElement de = new DataElement();
		Binding b = de.addElement().getBinding();
		b.setName("BINDING");

		Organization o = new Organization();
		o.setName("ORG");
		b.addUndeclaredExtension(new ExtensionDt(false, "urn:foo", new ResourceReferenceDt(o)));

		String str = ourCtx.newXmlParser().encodeResourceToString(de);
		ourLog.info(str);
		
		de = ourCtx.newXmlParser().parseResource(DataElement.class, str);
		b = de.getElement().get(0).getBinding();
		assertEquals("BINDING", b.getName());
		
		List<ExtensionDt> exts = b.getUndeclaredExtensionsByUrl("urn:foo");
		assertEquals(1, exts.size());
		ResourceReferenceDt rr = (ResourceReferenceDt)exts.get(0).getValue();
		o = (Organization) rr.getResource();
		assertEquals("ORG", o.getName());

	}

	@Test
	public void testParseAndEncodeExtensionOnResourceReference() {
		//@formatter:off
		String input = "<DataElement>" + 
				"<id value=\"gender\"/>"+ 
				"<contained>"+ 
				"<ValueSet>"+ 
				"<id value=\"2179414\"/>"+ 
				"<url value=\"2179414\"/>"+ 
				"<version value=\"1.0\"/>"+ 
				"<name value=\"Gender Code\"/>"+ 
				"<description value=\"All codes representing the gender of a person.\"/>"+ 
				"<status value=\"active\"/>"+ 
				"<compose>"+ 
				"<include>"+ 
				"<system value=\"http://ncit.nci.nih.gov\"/>"+ 
				"<concept>"+ 
				"<code value=\"C17998\"/>"+ 
				"<display value=\"Unknown\"/>"+ 
				"</concept>"+ 
				"<concept>"+ 
				"<code value=\"C20197\"/>"+ 
				"<display value=\"Male\"/>"+ 
				"</concept>"+ 
				"<concept>"+ 
				"<code value=\"C16576\"/>"+ 
				"<display value=\"Female\"/>"+ 
				"</concept>"+ 
				"<concept>"+ 
				"<code value=\"C38046\"/>"+ 
				"<display value=\"Not specified\"/>"+ 
				"</concept>"+ 
				"</include>"+ 
				"</compose>"+ 
				"</ValueSet>"+ 
				"</contained>"+ 
				"<contained>"+ 
				"<ValueSet>"+ 
				"<id value=\"2179414-permitted\"/>"+ 
				"<status value=\"active\"/>"+ 
				"<define>"+ 
				"<system value=\"http://example.org/fhir/2179414\"/>"+ 
				"<caseSensitive value=\"true\"/>"+ 
				"<concept>"+ 
				"<code value=\"0\"/>"+ 
				"</concept>"+ 
				"<concept>"+ 
				"<code value=\"1\"/>"+ 
				"</concept>"+ 
				"<concept>"+ 
				"<code value=\"2\"/>"+ 
				"</concept>"+ 
				"<concept>"+ 
				"<code value=\"3\"/>"+ 
				"</concept>"+ 
				"</define>"+ 
				"</ValueSet>"+ 
				"</contained>"+ 
				"<contained>"+ 
				"<ConceptMap>"+ 
				"<id value=\"2179414-cm\"/>"+ 
				"<status value=\"active\"/>"+ 
				"<sourceReference>"+ 
				"<reference value=\"#2179414\"/>"+ 
				"</sourceReference>"+ 
				"<targetReference>"+ 
				"<reference value=\"#2179414-permitted\"/>"+ 
				"</targetReference>"+ 
				"<element>"+ 
				"<code value=\"C17998\"/>"+ 
				"<map>"+ 
				"<code value=\"0\"/>"+ 
				"<equivalence value=\"equal\"/>"+ 
				"</map>"+ 
				"</element>"+ 
				"<element>"+ 
				"<code value=\"C20197\"/>"+ 
				"<map>"+ 
				"<code value=\"1\"/>"+ 
				"<equivalence value=\"equal\"/>"+ 
				"</map>"+ 
				"</element>"+ 
				"<element>"+ 
				"<code value=\"C16576\"/>"+ 
				"<map>"+ 
				"<code value=\"2\"/>"+ 
				"<equivalence value=\"equal\"/>"+ 
				"</map>"+ 
				"</element>"+ 
				"<element>"+ 
				"<code value=\"C38046\"/>"+ 
				"<map>"+ 
				"<code value=\"3\"/>"+ 
				"<equivalence value=\"equal\"/>"+ 
				"</map>"+ 
				"</element>"+ 
				"</ConceptMap>"+ 
				"</contained>"+ 
				"<identifier>"+ 
				"<value value=\"2179650\"/>"+ 
				"</identifier>"+ 
				"<version value=\"1.0\"/>"+ 
				"<name value=\"Gender Code\"/>"+ 
				"<useContext>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/FBPP\"/>"+ 
				"<display value=\"FBPP Pooled Database\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/PhenX\"/>"+ 
				"<display value=\"Demographics\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/EligibilityCriteria\"/>"+ 
				"<display value=\"Pt. Administrative\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/UAMSClinicalResearch\"/>"+ 
				"<display value=\"UAMS New CDEs\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/PhenX\"/>"+ 
				"<display value=\"Substance Abuse and \"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Category\"/>"+ 
				"<display value=\"CSAERS Adverse Event\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/PhenX\"/>"+ 
				"<display value=\"Core: Tier 1\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Category\"/>"+ 
				"<display value=\"Case Report Forms\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Category\"/>"+ 
				"<display value=\"CSAERS Review Set\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Demonstration%20Applications\"/>"+ 
				"<display value=\"CIAF\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/NIDA%20CTN%20Usage\"/>"+ 
				"<display value=\"Clinical Research\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/NIDA%20CTN%20Usage\"/>"+ 
				"<display value=\"Electronic Health Re\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Condition\"/>"+ 
				"<display value=\"Barretts Esophagus\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Condition\"/>"+ 
				"<display value=\"Bladder Cancer\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Condition\"/>"+ 
				"<display value=\"Oral Leukoplakia\"/>"+ 
				"</coding>"+ 
				"<coding>"+ 
				"<system value=\"http://example.org/Condition\"/>"+ 
				"<display value=\"Sulindac for Breast\"/>"+ 
				"</coding>"+ 
				"</useContext>"+ 
				"<status value=\"active\"/>"+ 
				"<publisher value=\"DCP\"/>"+ 
				"<element>"+ 
				"<extension url=\"http://hl7.org/fhir/StructureDefinition/minLength\">"+ 
				"<valueInteger value=\"1\"/>"+ 
				"</extension>"+ 
				"<extension url=\"http://hl7.org/fhir/StructureDefinition/elementdefinition-question\">"+ 
				"<valueString value=\"Gender\"/>"+ 
				"</extension>"+ 
				"<path value=\"Gender\"/>"+ 
				"<definition value=\"The code representing the gender of a person.\"/>"+ 
				"<type>"+ 
				"<code value=\"CodeableConcept\"/>"+ 
				"</type>"+ 
				"<maxLength value=\"13\"/>"+ 
				"<binding>"+ 
				"<name value=\"Gender\"/>"+ 
				"<strength value=\"required\"/>"+ 
				"<valueSetReference>"+ 
				"<extension url=\"http://hl7.org/fhir/StructureDefinition/11179-permitted-value-valueset\">"+ 
				"<valueReference>"+ 
				"<reference value=\"#2179414-permitted\"/>"+ 
				"</valueReference>"+ 
				"</extension>"+ 
				"<extension url=\"http://hl7.org/fhir/StructureDefinition/11179-permitted-value-conceptmap\">"+ 
				"<valueReference>"+ 
				"<reference value=\"#2179414-cm\"/>"+ 
				"</valueReference>"+ 
				"</extension>"+ 
				"<reference value=\"#2179414\"/>"+ 
				"</valueSetReference>"+ 
				"</binding>"+ 
				"</element>"+ 
				"</DataElement>";
		//@formatter:on
		DataElement de = ourCtx.newXmlParser().parseResource(DataElement.class, input);
		String output = ourCtx.newXmlParser().encodeResourceToString(de).replace(" xmlns=\"http://hl7.org/fhir\"", "");
		
		ElementDefinitionDt elem = de.getElement().get(0);
		Binding b = elem.getBinding();
		assertEquals("Gender", b.getName());
		
		ResourceReferenceDt ref = (ResourceReferenceDt) b.getValueSet();
		assertEquals("#2179414", ref.getReference().getValue());
		
		assertEquals(2, ref.getUndeclaredExtensions().size());
		ExtensionDt ext = ref.getUndeclaredExtensions().get(0);
		assertEquals("http://hl7.org/fhir/StructureDefinition/11179-permitted-value-valueset", ext.getUrl());
		assertEquals(ResourceReferenceDt.class, ext.getValue().getClass());
		assertEquals("#2179414-permitted", ((ResourceReferenceDt)ext.getValue()).getReference().getValue());
		
		ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(de));
		
		assertThat(output, containsString("http://hl7.org/fhir/StructureDefinition/11179-permitted-value-valueset"));
		
		ourLog.info("Expected: {}", input);
		ourLog.info("Actual  : {}", output);
		assertEquals(input, output);
	}
	
	@Test
	public void testEncodeBinaryWithNoContentType() {
		Binary b = new Binary();
		b.setContent(new byte[] {1,2,3,4});
		
		String output = ourCtx.newXmlParser().encodeResourceToString(b);
		ourLog.info(output);
		
		assertEquals("<Binary xmlns=\"http://hl7.org/fhir\"><content value=\"AQIDBA==\"/></Binary>", output);
	}
	
	@Test
	public void testMoreExtensions() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setUse(IdentifierUseEnum.OFFICIAL).setSystem("urn:example").setValue("7000135");

		ExtensionDt ext = new ExtensionDt();
		ext.setUrl("http://example.com/extensions#someext");
		ext.setValue(new DateTimeDt("2011-01-02T11:13:15"));

		// Add the extension to the resource
		patient.addUndeclaredExtension(ext);
		// END SNIPPET: resourceExtension

		// START SNIPPET: resourceStringExtension
		HumanNameDt name = patient.addName();
		name.addFamily("Shmoe");
		StringDt given = name.addGiven();
		given.setValue("Joe");
		ExtensionDt ext2 = new ExtensionDt().setUrl("http://examples.com#givenext").setValue(new StringDt("given"));
		given.addUndeclaredExtension(ext2);

		StringDt given2 = name.addGiven();
		given2.setValue("Shmoe");
		ExtensionDt given2ext = new ExtensionDt().setUrl("http://examples.com#givenext_parent");
		given2.addUndeclaredExtension(given2ext);
		ExtensionDt givenExtChild = new ExtensionDt();
		givenExtChild.setUrl("http://examples.com#givenext_child").setValue(new StringDt("CHILD"));
		given2ext.addUndeclaredExtension(givenExtChild);
		// END SNIPPET: resourceStringExtension

		// START SNIPPET: subExtension
		ExtensionDt parent = new ExtensionDt().setUrl("http://example.com#parent");
		patient.addUndeclaredExtension(parent);

		ExtensionDt child1 = new ExtensionDt().setUrl("http://example.com#child").setValue(new StringDt("value1"));
		parent.addUndeclaredExtension(child1);

		ExtensionDt child2 = new ExtensionDt().setUrl("http://example.com#child").setValue(new StringDt("value1"));
		parent.addUndeclaredExtension(child2);
		// END SNIPPET: subExtension

		String output = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(patient);
		ourLog.info(output);

		String enc = ourCtx.newXmlParser().encodeResourceToString(patient);
		assertThat(enc, containsString("<Patient xmlns=\"http://hl7.org/fhir\"><extension url=\"http://example.com/extensions#someext\"><valueDateTime value=\"2011-01-02T11:13:15\"/></extension>"));
		assertThat(
				enc,
				containsString("<extension url=\"http://example.com#parent\"><extension url=\"http://example.com#child\"><valueString value=\"value1\"/></extension><extension url=\"http://example.com#child\"><valueString value=\"value1\"/></extension></extension>"));
		assertThat(enc, containsString("<given value=\"Joe\"><extension url=\"http://examples.com#givenext\"><valueString value=\"given\"/></extension></given>"));
		assertThat(enc, containsString("<given value=\"Shmoe\"><extension url=\"http://examples.com#givenext_parent\"><extension url=\"http://examples.com#givenext_child\"><valueString value=\"CHILD\"/></extension></extension></given>"));
	}

	
	@Test
	public void testEncodeNonContained() {
		// Create an organization
		Organization org = new Organization();
		org.setId("Organization/65546");
		org.getNameElement().setValue("Contained Test Organization");

		// Create a patient
		Patient patient = new Patient();
		patient.setId("Patient/1333");
		patient.addIdentifier().setSystem("urn:mrns").setValue("253345");
		patient.getManagingOrganization().setResource(org);
		
		// Create a list containing both resources. In a server method, you might just
		// return this list, but here we will create a bundle to encode.
		List<IBaseResource> resources = new ArrayList<IBaseResource>();
		resources.add(org);
		resources.add(patient);		
		
		// Create a bundle with both
		ca.uhn.fhir.model.dstu2.resource.Bundle b = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		b.addEntry().setResource(org);
		b.addEntry().setResource(patient);
		
		// Encode the buntdle
		String encoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(b);
		ourLog.info(encoded);
		assertThat(encoded, not(containsString("<contained>")));
		assertThat(encoded, stringContainsInOrder("<Organization", "<id value=\"65546\"/>", "</Organization>"));
		assertThat(encoded, containsString("<reference value=\"Organization/65546\"/>"));
		assertThat(encoded, stringContainsInOrder("<Patient", "<id value=\"1333\"/>", "</Patient>"));
		
		encoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(patient);
		ourLog.info(encoded);
		assertThat(encoded, not(containsString("<contained>")));
		assertThat(encoded, containsString("<reference value=\"Organization/65546\"/>"));
		
		
	}

	@Test
	public void testParseNarrative() throws Exception {
		//@formatter:off
		String htmlNoNs = "<div>AAA<b>BBB</b>CCC</div>";
		String htmlNs = htmlNoNs.replace("<div>", "<div xmlns=\"http://www.w3.org/1999/xhtml\">"); 
		String res= "<Patient xmlns=\"http://hl7.org/fhir\">\n" + 
				"   <id value=\"1333\"/>\n" + 
				"   <text>\n" + 
				"      " + htmlNs + "\n" +
				"   </text>\n" + 
				"</Patient>";
		//@formatter:on
		
		Patient p = ourCtx.newXmlParser().parseResource(Patient.class, res);
		assertEquals(htmlNs, p.getText().getDiv().getValueAsString());
	}
	
	/**
	 * Thanks to Alexander Kley!
	 */
	@Test
	public void testParseContainedBinaryResource() {
		byte[] bin = new byte[] { 0, 1, 2, 3, 4 };
		final Binary binary = new Binary();
		binary.setContentType("PatientConsent").setContent(bin);
		// binary.setId(UUID.randomUUID().toString());

		ca.uhn.fhir.model.dstu2.resource.DocumentManifest manifest = new ca.uhn.fhir.model.dstu2.resource.DocumentManifest();
		// manifest.setId(UUID.randomUUID().toString());
		CodeableConceptDt cc = new CodeableConceptDt();
		cc.addCoding().setSystem("mySystem").setCode("PatientDocument");
		manifest.setType(cc);
		manifest.setMasterIdentifier(new IdentifierDt().setSystem("mySystem").setValue(UUID.randomUUID().toString()));
		manifest.addContent().setP(new ResourceReferenceDt(binary));
		manifest.setStatus(DocumentReferenceStatusEnum.CURRENT);

		String encoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(manifest);
		ourLog.info(encoded);
		assertThat(encoded, StringContainsInOrder.stringContainsInOrder(Arrays.asList("contained>", "<Binary", "</contained>")));

		ca.uhn.fhir.model.dstu2.resource.DocumentManifest actual = ourCtx.newXmlParser().parseResource(ca.uhn.fhir.model.dstu2.resource.DocumentManifest.class, encoded);
		assertEquals(1, actual.getContained().getContainedResources().size());
		assertEquals(1, actual.getContent().size());
		assertNotNull(((ResourceReferenceDt)actual.getContent().get(0).getP()).getResource());

	}

	@Test
	public void testEncodeAndParseContained() {
		IParser xmlParser = ourCtx.newXmlParser().setPrettyPrint(true);

		// Create an organization, note that the organization does not have an ID
		Organization org = new Organization();
		org.getNameElement().setValue("Contained Test Organization");

		// Create a patient
		Patient patient = new Patient();
		patient.setId("Patient/1333");
		patient.addIdentifier().setSystem("urn:mrns").setValue("253345");

		// Put the organization as a reference in the patient resource
		patient.getManagingOrganization().setResource(org);

		String encoded = xmlParser.encodeResourceToString(patient);
		ourLog.info(encoded);
		assertThat(encoded, containsString("<contained>"));
		assertThat(encoded, containsString("<reference value=\"#1\"/>"));

		// Create a bundle with just the patient resource
		ca.uhn.fhir.model.dstu2.resource.Bundle b = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		b.addEntry().setResource(patient);

		// Encode the bundle
		encoded = xmlParser.encodeResourceToString(b);
		ourLog.info(encoded);
		assertThat(encoded, stringContainsInOrder(Arrays.asList("<contained>", "<id value=\"1\"/>", "</contained>")));
		assertThat(encoded, containsString("<reference value=\"#1\"/>"));
		assertThat(encoded, stringContainsInOrder(Arrays.asList("<entry>", "</entry>")));
		assertThat(encoded, not(stringContainsInOrder(Arrays.asList("<entry>", "</entry>", "<entry>"))));

		// Re-parse the bundle
		patient = (Patient) xmlParser.parseResource(xmlParser.encodeResourceToString(patient));
		assertEquals("#1", patient.getManagingOrganization().getReference().getValue());

		assertNotNull(patient.getManagingOrganization().getResource());
		org = (Organization) patient.getManagingOrganization().getResource();
		assertEquals("#1", org.getId().getValue());
		assertEquals("Contained Test Organization", org.getName());

		// And re-encode a second time
		encoded = xmlParser.encodeResourceToString(patient);
		ourLog.info(encoded);
		assertThat(encoded, stringContainsInOrder(Arrays.asList("<contained>", "<Organization ", "<id value=\"1\"/>", "</Organization", "</contained>", "<reference value=\"#1\"/>")));
		assertThat(encoded, not(stringContainsInOrder(Arrays.asList("<contained>", "<Org", "<contained>"))));
		assertThat(encoded, containsString("<reference value=\"#1\"/>"));

		// And re-encode once more, with the references cleared
		patient.getContained().getContainedResources().clear();
		patient.getManagingOrganization().setReference((String)null);
		encoded = xmlParser.encodeResourceToString(patient);
		ourLog.info(encoded);
		assertThat(encoded, stringContainsInOrder(Arrays.asList("<contained>", "<Organization ", "<id value=\"1\"/>", "</Organization", "</contained>", "<reference value=\"#1\"/>")));
		assertThat(encoded, not(stringContainsInOrder(Arrays.asList("<contained>", "<Org", "<contained>"))));
		assertThat(encoded, containsString("<reference value=\"#1\"/>"));

		// And re-encode once more, with the references cleared and a manually set local ID
		patient.getContained().getContainedResources().clear();
		patient.getManagingOrganization().setReference((String)null);
		patient.getManagingOrganization().getResource().setId(("#333"));
		encoded = xmlParser.encodeResourceToString(patient);
		ourLog.info(encoded);
		assertThat(encoded, stringContainsInOrder(Arrays.asList("<contained>", "<Organization ", "<id value=\"333\"/>", "</Organization", "</contained>", "<reference value=\"#333\"/>")));
		assertThat(encoded, not(stringContainsInOrder(Arrays.asList("<contained>", "<Org", "<contained>"))));

	}

	
	@Test
	public void testEncodeContainedWithNarrativeIsSuppresed() throws Exception {
		IParser parser = ourCtx.newXmlParser().setPrettyPrint(true);

		// Create an organization, note that the organization does not have an ID
		Organization org = new Organization();
		org.getNameElement().setValue("Contained Test Organization");
		org.getText().setDiv("<div>FOOBAR</div>");

		// Create a patient
		Patient patient = new Patient();
		patient.setId("Patient/1333");
		patient.addIdentifier().setSystem("urn:mrns").setValue("253345");
		patient.getText().setDiv("<div>BARFOO</div>");
		patient.getManagingOrganization().setResource(org);

		String encoded = parser.encodeResourceToString(patient);
		ourLog.info(encoded);
		
		assertThat(encoded, stringContainsInOrder("<Patient", "<text>", "<div xmlns=\"http://www.w3.org/1999/xhtml\">BARFOO</div>", "<contained>", "<Organization", "</Organization"));
		assertThat(encoded, not(stringContainsInOrder("<Patient", "<text>", "<contained>", "<Organization", "<text", "</Organization")));
		
		assertThat(encoded, not(containsString("FOOBAR")));
		assertThat(encoded, (containsString("BARFOO")));

	}


	
	/**
	 * see #144 and #146
	 */
	@Test
	public void testParseContained() {

		FhirContext c = FhirContext.forDstu2();
		IParser parser = c.newXmlParser().setPrettyPrint(true);

		Observation o = new Observation();
		o.getCode().setText("obs text");

		Patient p = new Patient();
		p.addName().addFamily("patient family");
		o.getSubject().setResource(p);
		
		String enc = parser.encodeResourceToString(o);
		ourLog.info(enc);
		
		//@formatter:off
		assertThat(enc, stringContainsInOrder(
			"<Observation xmlns=\"http://hl7.org/fhir\">",
			"<contained>",
			"<Patient xmlns=\"http://hl7.org/fhir\">",
			"<id value=\"1\"/>",
			"</contained>",
			"<reference value=\"#1\"/>"
			));
		//@formatter:on
		
		o = parser.parseResource(Observation.class, enc);
		assertEquals("obs text", o.getCode().getText());
		
		assertNotNull(o.getSubject().getResource());
		p = (Patient) o.getSubject().getResource();
		assertEquals("patient family", p.getNameFirstRep().getFamilyAsSingleString());
	}

	/**
	 * See #113
	 */
	@Test
	public void testEncodeContainedResources() {
		
		MedicationPrescription medicationPrescript = new MedicationPrescription();
		
		String medId = "123";
		CodeableConceptDt codeDt = new CodeableConceptDt("urn:sys", "code1");

		// Adding medication to Contained.
		Medication medResource = new Medication();
		medResource.setCode(codeDt);
		medResource.setId("#" + String.valueOf(medId));
		ArrayList<IResource> medResList = new ArrayList<IResource>();
		medResList.add(medResource);
		ContainedDt medContainedDt = new ContainedDt();
		medContainedDt.setContainedResources(medResList);
		medicationPrescript.setContained(medContainedDt);

		// Medication reference. This should point to the contained resource.
		ResourceReferenceDt medRefDt = new ResourceReferenceDt("#" + medId);
		medRefDt.setDisplay("MedRef");
		medicationPrescript.setMedication(medRefDt);
		
		IParser p = ourCtx.newXmlParser().setPrettyPrint(true);
		String encoded = p.encodeResourceToString(medicationPrescript);
		ourLog.info(encoded);
		
		//@formatter:on
		assertThat(encoded, stringContainsInOrder(
			"<MedicationPrescription xmlns=\"http://hl7.org/fhir\">",
			"<contained>", 
			"<Medication xmlns=\"http://hl7.org/fhir\">", 
			"<id value=\"123\"/>", 
			"<code>", 
			"<coding>", 
			"<system value=\"urn:sys\"/>", 
			"<code value=\"code1\"/>", 
			"</coding>", 
			"</code>", 
			"</Medication>", 
			"</contained>", 
			"<medication>", 
			"<reference value=\"#123\"/>", 
			"<display value=\"MedRef\"/>", 
			"</medication>", 
			"</MedicationPrescription>"));
		//@formatter:off

	}
	
	/**
	 * See #113
	 */
	@Test
	public void testEncodeContainedResourcesManualContainUsingNonLocalId() {
		
		MedicationPrescription medicationPrescript = new MedicationPrescription();
		
		String medId = "123";
		CodeableConceptDt codeDt = new CodeableConceptDt("urn:sys", "code1");

		// Adding medication to Contained.
		Medication medResource = new Medication();
		medResource.setCode(codeDt);
		medResource.setId(String.valueOf(medId)); // ID does not start with '#'
		ArrayList<IResource> medResList = new ArrayList<IResource>();
		medResList.add(medResource);
		ContainedDt medContainedDt = new ContainedDt();
		medContainedDt.setContainedResources(medResList);
		medicationPrescript.setContained(medContainedDt);

		// Medication reference. This should point to the contained resource.
		ResourceReferenceDt medRefDt = new ResourceReferenceDt("#" + medId);
		medRefDt.setDisplay("MedRef");
		medicationPrescript.setMedication(medRefDt);
		
		IParser p = ourCtx.newXmlParser().setPrettyPrint(true);
		String encoded = p.encodeResourceToString(medicationPrescript);
		ourLog.info(encoded);
		
		//@formatter:on
		assertThat(encoded, stringContainsInOrder(
			"<MedicationPrescription xmlns=\"http://hl7.org/fhir\">",
			"<contained>", 
			"<Medication xmlns=\"http://hl7.org/fhir\">", 
			"<id value=\"123\"/>", 
			"<code>", 
			"<coding>", 
			"<system value=\"urn:sys\"/>", 
			"<code value=\"code1\"/>", 
			"</coding>", 
			"</code>", 
			"</Medication>", 
			"</contained>", 
			"<medication>", 
			"<reference value=\"#123\"/>", 
			"<display value=\"MedRef\"/>", 
			"</medication>", 
			"</MedicationPrescription>"));
		//@formatter:off

	}

	/**
	 * See #113
	 */
	@Test
	public void testEncodeContainedResourcesAutomatic() {
		
		MedicationPrescription medicationPrescript = new MedicationPrescription();
		String nameDisp = "MedRef";
		CodeableConceptDt codeDt = new CodeableConceptDt("urn:sys", "code1");
		
		// Adding medication to Contained.
		Medication medResource = new Medication();
		// No ID set
		medResource.setCode(codeDt);

		// Medication reference. This should point to the contained resource.
		ResourceReferenceDt medRefDt = new ResourceReferenceDt();
		medRefDt.setDisplay(nameDisp);
		// Resource reference set, but no ID
		medRefDt.setResource(medResource);
		medicationPrescript.setMedication(medRefDt);
		
		IParser p = ourCtx.newXmlParser().setPrettyPrint(true);
		String encoded = p.encodeResourceToString(medicationPrescript);
		ourLog.info(encoded);
		
		//@formatter:on
		assertThat(encoded, stringContainsInOrder(
			"<MedicationPrescription xmlns=\"http://hl7.org/fhir\">",
			"<contained>", 
			"<Medication xmlns=\"http://hl7.org/fhir\">", 
			"<id value=\"1\"/>", 
			"<code>", 
			"<coding>", 
			"<system value=\"urn:sys\"/>", 
			"<code value=\"code1\"/>", 
			"</coding>", 
			"</code>", 
			"</Medication>", 
			"</contained>", 
			"<medication>", 
			"<reference value=\"#1\"/>", 
			"<display value=\"MedRef\"/>", 
			"</medication>", 
			"</MedicationPrescription>"));
		//@formatter:off
	}

	@Test
	public void testEncodeAndParseSecurityLabels() {
		Patient p = new Patient();
		p.addName().addFamily("FAMILY");
		
		List<BaseCodingDt> labels = new ArrayList<BaseCodingDt>();
		labels.add(new CodingDt().setSystem("SYSTEM1").setCode("CODE1").setDisplay("DISPLAY1").setPrimary(true).setVersion("VERSION1"));
		labels.add(new CodingDt().setSystem("SYSTEM2").setCode("CODE2").setDisplay("DISPLAY2").setPrimary(false).setVersion("VERSION2"));
		
		ResourceMetadataKeyEnum.SECURITY_LABELS.put(p, labels);

		String enc = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(p);
		ourLog.info(enc);
		
		//@formatter:off
		assertThat(enc, stringContainsInOrder("<Patient xmlns=\"http://hl7.org/fhir\">", 
			"<meta>", 
			"<security>", 
			"<system value=\"SYSTEM1\"/>", 
			"<version value=\"VERSION1\"/>", 
			"<code value=\"CODE1\"/>", 
			"<display value=\"DISPLAY1\"/>", 
			"<primary value=\"true\"/>", 
			"</security>", 
			"<security>", 
			"<system value=\"SYSTEM2\"/>", 
			"<version value=\"VERSION2\"/>", 
			"<code value=\"CODE2\"/>", 
			"<display value=\"DISPLAY2\"/>", 
			"<primary value=\"false\"/>", 
			"</security>",
			"</meta>", 
			"<name>", 
			"<family value=\"FAMILY\"/>", 
			"</name>", 
			"</Patient>"));
		//@formatter:on
		
		Patient parsed = ourCtx.newXmlParser().parseResource(Patient.class, enc);
		List<BaseCodingDt> gotLabels = ResourceMetadataKeyEnum.SECURITY_LABELS.get(parsed);
		
		assertEquals(2,gotLabels.size());

		CodingDt label = (CodingDt) gotLabels.get(0);
		assertEquals("SYSTEM1", label.getSystem());
		assertEquals("CODE1", label.getCode());
		assertEquals("DISPLAY1", label.getDisplay());
		assertEquals(true, label.getPrimary());
		assertEquals("VERSION1", label.getVersion());

		label = (CodingDt) gotLabels.get(1);
		assertEquals("SYSTEM2", label.getSystem());
		assertEquals("CODE2", label.getCode());
		assertEquals("DISPLAY2", label.getDisplay());
		assertEquals(false, label.getPrimary());
		assertEquals("VERSION2", label.getVersion());
	}

	@Test
	public void testEncodeAndParseMetaProfileAndTags() {
		Patient p = new Patient();
		p.addName().addFamily("FAMILY");
		
		List<IdDt> profiles = new ArrayList<IdDt>();
		profiles.add(new IdDt("http://foo/Profile1"));
		profiles.add(new IdDt("http://foo/Profile2"));
		ResourceMetadataKeyEnum.PROFILES.put(p, profiles);

		TagList tagList = new TagList();
		tagList.addTag("scheme1", "term1", "label1");
		tagList.addTag("scheme2", "term2", "label2");
		ResourceMetadataKeyEnum.TAG_LIST.put(p, tagList);
		
		String enc = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(p);
		ourLog.info(enc);
		
		//@formatter:off
		assertThat(enc, stringContainsInOrder("<Patient xmlns=\"http://hl7.org/fhir\">", 
			"<meta>",
			"<meta>",
			"<profile value=\"http://foo/Profile1\"/>",
			"<profile value=\"http://foo/Profile2\"/>",
			"<tag>",
			"<system value=\"scheme1\"/>",
			"<code value=\"term1\"/>",
			"<display value=\"label1\"/>",
			"</tag>",
			"<tag>",
			"<system value=\"scheme2\"/>",
			"<code value=\"term2\"/>",
			"<display value=\"label2\"/>",
			"</tag>",
			"</meta>",
			"</meta>",
			"<name>",
			"<family value=\"FAMILY\"/>",
			"</name>", 
			"</Patient>"));
		//@formatter:on
		
		Patient parsed = ourCtx.newXmlParser().parseResource(Patient.class, enc);
		List<IdDt> gotLabels = ResourceMetadataKeyEnum.PROFILES.get(parsed);
		
		assertEquals(2,gotLabels.size());

		IdDt label = (IdDt) gotLabels.get(0);
		assertEquals("http://foo/Profile1", label.getValue());
		label = (IdDt) gotLabels.get(1);
		assertEquals("http://foo/Profile2", label.getValue());
		
		tagList = ResourceMetadataKeyEnum.TAG_LIST.get(parsed);
		assertEquals(2, tagList.size());
		
		assertEquals(new Tag("scheme1", "term1", "label1"), tagList.get(0));
		assertEquals(new Tag("scheme2", "term2", "label2"), tagList.get(1));
	}
	
	@Test
	public void testDuration() {
		Encounter enc = new Encounter();
		DurationDt duration = new DurationDt();
		duration.setUnits("day").setValue(123L);
		enc.setLength(duration);
		
		String str = ourCtx.newXmlParser().encodeResourceToString(enc);
		ourLog.info(str);
		
		assertThat(str, not(containsString("meta")));
		assertThat(str, containsString("<length><value value=\"123\"/><units value=\"day\"/></length>"));
	}
	
	@Test
	public void testParseBundleWithBinary() {
		// TODO: implement this test, make sure we handle ID and meta correctly in Binary
	}

	@Test
	public void testParseAndEncodeBundle() throws Exception {
		String content = IOUtils.toString(XmlParserDstu2Test.class.getResourceAsStream("/bundle-example.xml"));

		Bundle parsed = ourCtx.newXmlParser().parseBundle(content);
		assertEquals("http://example.com/base/Bundle/example/_history/1", parsed.getId().getValue());
		assertEquals("1", parsed.getResourceMetadata().get(ResourceMetadataKeyEnum.VERSION));
		assertEquals("1", parsed.getId().getVersionIdPart());
		assertEquals(new InstantDt("2014-08-18T01:43:30Z"), parsed.getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED));
		assertEquals("searchset", parsed.getType().getValue());
		assertEquals(3, parsed.getTotalResults().getValue().intValue());
		assertEquals("http://example.com/base", parsed.getLinkBase().getValue());
		assertEquals("https://example.com/base/MedicationPrescription?patient=347&searchId=ff15fd40-ff71-4b48-b366-09c706bed9d0&page=2", parsed.getLinkNext().getValue());
		assertEquals("https://example.com/base/MedicationPrescription?patient=347&_include=MedicationPrescription.medication", parsed.getLinkSelf().getValue());

		assertEquals(2, parsed.getEntries().size());
		assertEquals("http://foo?search", parsed.getEntries().get(0).getLinkSearch().getValue());

		MedicationPrescription p = (MedicationPrescription) parsed.getEntries().get(0).getResource();
		assertEquals("Patient/347", p.getPatient().getReference().getValue());
		assertEquals("2014-08-16T05:31:17Z", ResourceMetadataKeyEnum.UPDATED.get(p).getValueAsString());
		assertEquals("http://example.com/base/MedicationPrescription/3123/_history/1", p.getId().getValue());

		Medication m = (Medication) parsed.getEntries().get(1).getResource();
		assertEquals("http://example.com/base/Medication/example", m.getId().getValue());
		assertSame(p.getMedication().getResource(), m);

		String reencoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeBundleToString(parsed);
		ourLog.info(reencoded);

		Diff d = new Diff(new StringReader(content), new StringReader(reencoded));
		assertTrue(d.toString(), d.identical());

	}

	@Test
	public void testParseAndEncodeBundleNewStyle() throws Exception {
		String content = IOUtils.toString(XmlParserDstu2Test.class.getResourceAsStream("/bundle-example.xml"));

		IParser newXmlParser = ourCtx.newXmlParser();
		ca.uhn.fhir.model.dstu2.resource.Bundle parsed = newXmlParser.parseResource(ca.uhn.fhir.model.dstu2.resource.Bundle.class, content);
		assertEquals("http://example.com/base/Bundle/example/_history/1", parsed.getId().getValue());
		assertEquals("1", parsed.getResourceMetadata().get(ResourceMetadataKeyEnum.VERSION));
		assertEquals(new InstantDt("2014-08-18T01:43:30Z"), parsed.getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED));
		assertEquals("searchset", parsed.getType());
		assertEquals(3, parsed.getTotal().intValue());
		assertEquals("http://example.com/base", parsed.getBaseElement().getValueAsString());
		assertEquals("https://example.com/base/MedicationPrescription?patient=347&searchId=ff15fd40-ff71-4b48-b366-09c706bed9d0&page=2", parsed.getLink().get(0).getUrlElement().getValueAsString());
		assertEquals("https://example.com/base/MedicationPrescription?patient=347&_include=MedicationPrescription.medication", parsed.getLink().get(1).getUrlElement().getValueAsString());

		assertEquals(2, parsed.getEntry().size());
		assertEquals("http://foo?search", parsed.getEntry().get(0).getTransaction().getUrlElement().getValueAsString());

		MedicationPrescription p = (MedicationPrescription) parsed.getEntry().get(0).getResource();
		assertEquals("Patient/347", p.getPatient().getReference().getValue());
		assertEquals("2014-08-16T05:31:17Z", ResourceMetadataKeyEnum.UPDATED.get(p).getValueAsString());
		assertEquals("http://example.com/base/MedicationPrescription/3123/_history/1", p.getId().getValue());
//		assertEquals("3123", p.getId().getValue());

		Medication m = (Medication) parsed.getEntry().get(1).getResource();
		assertEquals("http://example.com/base/Medication/example", m.getId().getValue());
		assertSame(p.getMedication().getResource(), m);

		String reencoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(parsed);
		ourLog.info(reencoded);

		Diff d = new Diff(new StringReader(content), new StringReader(reencoded));
		assertTrue(d.toString(), d.identical());

	}

	
	@Test
	public void testEncodeAndParseBundleWithoutResourceIds() {
		Organization org = new Organization();
		org.addIdentifier().setSystem("urn:system").setValue("someval");
		
		Bundle bundle = Bundle.withSingleResource(org);
		String str = ourCtx.newXmlParser().encodeBundleToString(bundle);
		ourLog.info(str);
		
		Bundle parsed = ourCtx.newXmlParser().parseBundle(str);
		assertThat(parsed.getEntries().get(0).getResource().getId().getValue(), emptyOrNullString());
		assertTrue(parsed.getEntries().get(0).getResource().getId().isEmpty());
	}
	
	@Test
	public void testBundleWithBinary() {
		//@formatter:off
		String bundle = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" + 
			"   <meta/>\n" + 
			"   <base value=\"http://localhost:52788\"/>\n" + 
			"   <total value=\"1\"/>\n" + 
			"   <link>\n" + 
			"      <relation value=\"self\"/>\n" + 
			"      <url value=\"http://localhost:52788/Binary?_pretty=true\"/>\n" + 
			"   </link>\n" + 
			"   <entry>\n" + 
			"      <resource>\n" + 
			"         <Binary xmlns=\"http://hl7.org/fhir\">\n" + 
			"            <id value=\"1\"/>\n" + 
			"            <meta/>\n" + 
			"            <contentType value=\"text/plain\"/>\n" + 
			"            <content value=\"AQIDBA==\"/>\n" + 
			"         </Binary>\n" + 
			"      </resource>\n" + 
			"   </entry>\n" + 
			"</Bundle>";
		//@formatter:on
		
		Bundle b = ourCtx.newXmlParser().parseBundle(bundle);
		assertEquals(1, b.getEntries().size());
		
		Binary bin = (Binary) b.getEntries().get(0).getResource();
		assertArrayEquals(new byte[] {1,2,3,4}, bin.getContent());
		
	}
	

	@Test
	public void testParseMetadata() throws Exception {
		//@formatter:off
		String bundle = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" + 
			"   <base value=\"http://foo/fhirBase1\"/>\n" + 
			"   <total value=\"1\"/>\n" + 
			"   <link>\n" + 
			"      <relation value=\"self\"/>\n" + 
			"      <url value=\"http://localhost:52788/Binary?_pretty=true\"/>\n" + 
			"   </link>\n" + 
			"   <entry>\n" + 
			"      <base value=\"http://foo/fhirBase2\"/>\n" + 
			"      <resource>\n" + 
			"         <Patient xmlns=\"http://hl7.org/fhir\">\n" + 
			"            <id value=\"1\"/>\n" + 
			"            <meta>\n" +
			"               <versionId value=\"2\"/>\n" +
			"               <lastUpdated value=\"2001-02-22T11:22:33-05:00\"/>\n" +
			"            </meta>\n" + 
			"            <birthDate value=\"2012-01-02\"/>\n" + 
			"         </Patient>\n" + 
			"      </resource>\n" + 
			"      <search>\n" +
			"         <mode value=\"match\"/>\n" +
			"         <score value=\"0.123\"/>\n" +
			"      </search>\n" +
			"      <transaction>\n" +
			"         <method value=\"POST\"/>\n" +
			"         <url value=\"http://foo/Patient?identifier=value\"/>\n" +
			"      </transaction>\n" +
			"   </entry>\n" + 
			"</Bundle>";
		//@formatter:on
		
		Bundle b = ourCtx.newXmlParser().parseBundle(bundle);
		assertEquals(1, b.getEntries().size());
		
		Patient pt = (Patient) b.getEntries().get(0).getResource();
		assertEquals("http://foo/fhirBase2/Patient/1/_history/2", pt.getId().getValue());
		assertEquals("2012-01-02", pt.getBirthDateElement().getValueAsString());
		assertEquals("0.123", ResourceMetadataKeyEnum.ENTRY_SCORE.get(pt).getValueAsString());
		assertEquals("match", ResourceMetadataKeyEnum.ENTRY_SEARCH_MODE.get(pt).getCode());
		assertEquals("POST", ResourceMetadataKeyEnum.ENTRY_TRANSACTION_METHOD.get(pt).getCode());
		assertEquals("http://foo/Patient?identifier=value", ResourceMetadataKeyEnum.LINK_SEARCH.get(pt));
		assertEquals("2001-02-22T11:22:33-05:00", ResourceMetadataKeyEnum.UPDATED.get(pt).getValueAsString());
		
		Bundle toBundle = new Bundle();
		toBundle.getLinkBase().setValue("http://foo/fhirBase1");
		toBundle.getTotalResults().setValue(1);
		toBundle.getLinkSelf().setValue("http://localhost:52788/Binary?_pretty=true");
		
		toBundle.addResource(pt, ourCtx, "http://foo/fhirBase1");
		String reEncoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeBundleToString(toBundle);

		ourLog.info(reEncoded);
		
		Diff d = new Diff(new StringReader(bundle), new StringReader(reEncoded));
		assertTrue(d.toString(), d.identical());

	}

	/**
	 * See #103
	 */
	@Test
	public void testEncodeAndReEncodeContainedXml() {
		Composition comp = new Composition();
		comp.addSection().getContent().setResource(new AllergyIntolerance().setComment("Section0_Allergy0"));
		comp.addSection().getContent().setResource(new AllergyIntolerance().setComment("Section1_Allergy0"));
		comp.addSection().getContent().setResource(new AllergyIntolerance().setComment("Section2_Allergy0"));
		
		IParser parser = ourCtx.newXmlParser().setPrettyPrint(true);
		
		String string = parser.encodeResourceToString(comp);
		ourLog.info(string);

		Composition parsed = parser.parseResource(Composition.class, string);
		parsed.getSection().remove(0);

		string = parser.encodeResourceToString(parsed);
		ourLog.info(string);

		parsed = parser.parseResource(Composition.class, string);
		assertEquals(2, parsed.getContained().getContainedResources().size());
	}
	
	/**
	 * See #103
	 */
	@Test
	public void testEncodeAndReEncodeContainedJson() {
		Composition comp = new Composition();
		comp.addSection().getContent().setResource(new AllergyIntolerance().setComment("Section0_Allergy0"));
		comp.addSection().getContent().setResource(new AllergyIntolerance().setComment("Section1_Allergy0"));
		comp.addSection().getContent().setResource(new AllergyIntolerance().setComment("Section2_Allergy0"));
		
		IParser parser = ourCtx.newJsonParser().setPrettyPrint(true);
		
		String string = parser.encodeResourceToString(comp);
		ourLog.info(string);

		Composition parsed = parser.parseResource(Composition.class, string);
		parsed.getSection().remove(0);

		string = parser.encodeResourceToString(parsed);
		ourLog.info(string);

		parsed = parser.parseResource(Composition.class, string);
		assertEquals(2, parsed.getContained().getContainedResources().size());
	}

	@Test
	public void testEncodeAndParseExtensions() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setUse(IdentifierUseEnum.OFFICIAL).setSystem("urn:example").setValue("7000135");

		ExtensionDt ext = new ExtensionDt();
		ext.setUrl("http://example.com/extensions#someext");
		ext.setValue(new DateTimeDt("2011-01-02T11:13:15"));
		patient.addUndeclaredExtension(ext);

		ExtensionDt parent = new ExtensionDt().setUrl("http://example.com#parent");
		patient.addUndeclaredExtension(parent);
		ExtensionDt child1 = new ExtensionDt().setUrl("http://example.com#child").setValue(new StringDt("value1"));
		parent.addUndeclaredExtension(child1);
		ExtensionDt child2 = new ExtensionDt().setUrl("http://example.com#child").setValue(new StringDt("value2"));
		parent.addUndeclaredExtension(child2);

		ExtensionDt modExt = new ExtensionDt();
		modExt.setUrl("http://example.com/extensions#modext");
		modExt.setValue(new DateDt("1995-01-02"));
		modExt.setModifier(true);
		patient.addUndeclaredExtension(modExt);

		HumanNameDt name = patient.addName();
		name.addFamily("Blah");
		StringDt given = name.addGiven();
		given.setValue("Joe");
		ExtensionDt ext2 = new ExtensionDt().setUrl("http://examples.com#givenext").setValue(new StringDt("given"));
		given.addUndeclaredExtension(ext2);

		StringDt given2 = name.addGiven();
		given2.setValue("Shmoe");
		ExtensionDt given2ext = new ExtensionDt().setUrl("http://examples.com#givenext_parent");
		given2.addUndeclaredExtension(given2ext);
		given2ext.addUndeclaredExtension(new ExtensionDt().setUrl("http://examples.com#givenext_child").setValue(new StringDt("CHILD")));

		String output = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(patient);
		ourLog.info(output);

		String enc = ourCtx.newXmlParser().encodeResourceToString(patient);
		assertThat(enc, containsString("<Patient xmlns=\"http://hl7.org/fhir\"><extension url=\"http://example.com/extensions#someext\"><valueDateTime value=\"2011-01-02T11:13:15\"/></extension>"));
		assertThat(enc, containsString("<modifierExtension url=\"http://example.com/extensions#modext\"><valueDate value=\"1995-01-02\"/></modifierExtension>"));
		assertThat(
				enc,
				containsString("<extension url=\"http://example.com#parent\"><extension url=\"http://example.com#child\"><valueString value=\"value1\"/></extension><extension url=\"http://example.com#child\"><valueString value=\"value2\"/></extension></extension>"));
		assertThat(enc, containsString("<given value=\"Joe\"><extension url=\"http://examples.com#givenext\"><valueString value=\"given\"/></extension></given>"));
		assertThat(enc, containsString("<given value=\"Shmoe\"><extension url=\"http://examples.com#givenext_parent\"><extension url=\"http://examples.com#givenext_child\"><valueString value=\"CHILD\"/></extension></extension></given>"));
		
		/*
		 * Now parse this back
		 */

		Patient parsed = ourCtx.newXmlParser().parseResource(Patient.class, enc);
		ext = parsed.getUndeclaredExtensions().get(0);
		assertEquals("http://example.com/extensions#someext", ext.getUrl());
		assertEquals("2011-01-02T11:13:15", ((DateTimeDt) ext.getValue()).getValueAsString());

		parent = patient.getUndeclaredExtensions().get(1);
		assertEquals("http://example.com#parent", parent.getUrl());
		assertNull(parent.getValue());
		child1 = parent.getExtension().get(0);
		assertEquals("http://example.com#child", child1.getUrl());
		assertEquals("value1", ((StringDt) child1.getValue()).getValueAsString());
		child2 = parent.getExtension().get(1);
		assertEquals("http://example.com#child", child2.getUrl());
		assertEquals("value2", ((StringDt) child2.getValue()).getValueAsString());

		modExt = parsed.getUndeclaredModifierExtensions().get(0);
		assertEquals("http://example.com/extensions#modext", modExt.getUrl());
		assertEquals("1995-01-02", ((DateDt) modExt.getValue()).getValueAsString());

		name = parsed.getName().get(0);

		ext2 = name.getGiven().get(0).getUndeclaredExtensions().get(0);
		assertEquals("http://examples.com#givenext", ext2.getUrl());
		assertEquals("given", ((StringDt) ext2.getValue()).getValueAsString());

		given2ext = name.getGiven().get(1).getUndeclaredExtensions().get(0);
		assertEquals("http://examples.com#givenext_parent", given2ext.getUrl());
		assertNull(given2ext.getValue());
		ExtensionDt given2ext2 = given2ext.getExtension().get(0);
		assertEquals("http://examples.com#givenext_child", given2ext2.getUrl());
		assertEquals("CHILD", ((StringDt) given2ext2.getValue()).getValue());

	}

	
	
}
