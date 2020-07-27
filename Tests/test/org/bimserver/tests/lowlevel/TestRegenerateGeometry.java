package org.bimserver.tests.lowlevel;

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.*;
import org.bimserver.models.ifc4.*;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.shared.ChannelConnectionException;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.ServiceException;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.shared.interfaces.LowLevelInterface;
import org.bimserver.shared.interfaces.ServiceInterface;
import org.bimserver.test.TestWithEmbeddedServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.fail;

public class TestRegenerateGeometry extends TestWithEmbeddedServer {

	private BimServerClientInterface bimServerClient;
	private ServiceInterface serviceInterface;
	private LowLevelInterface lowLevelInterface;
	private long poid;
	private long renderEngineOid;

	@Before
	public void setup() throws ServiceException, ChannelConnectionException, MalformedURLException {
		// setup and checkin
		bimServerClient = getFactory().create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));
		lowLevelInterface = bimServerClient.getLowLevelInterface();
		serviceInterface = bimServerClient.getServiceInterface();
		renderEngineOid = bimServerClient.getPluginInterface().getDefaultRenderEngine().getOid();
		poid = serviceInterface.addProject("test" + Math.random(), "ifc4").getOid();
		SDeserializerPluginConfiguration deserializer = serviceInterface.getSuggestedDeserializerForExtension("ifc", poid);
		bimServerClient.checkinSync(poid, "test", deserializer.getOid(), false, new URL("https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/annex/annex-e/wall-with-opening-and-window.ifc")); // TODO add to test data repo
	}

	@Test
	public void testChangeAffectingMultipleProducts() throws ServerException, UserException, BimServerClientException {
		// change opening position: all 3 products (wall, opening, window) will have to be regenerated
		SProject project = serviceInterface.getProjectByPoid(poid);
		SRevision revision1 = serviceInterface.getRevision(project.getLastRevisionId());
		IfcModelInterface model = bimServerClient.getModel(project, revision1.getOid(), true, true, true);
		IfcOpeningElement opening = model.getFirst(IfcOpeningElement.class);
		IfcCartesianPoint openingLocation = ((IfcAxis2Placement3D)((IfcLocalPlacement) opening.getObjectPlacement()).getRelativePlacement()).getLocation();
		Assert.assertEquals("Unexpected original coordinates", openingLocation.getCoordinates(), Arrays.asList(1000.,0.,500.));
		long tid = lowLevelInterface.startTransaction(poid);
		lowLevelInterface.setDoubleAttributes(tid, openingLocation.getOid(), "Coordinates", Arrays.asList(1500.,0.,800.));
		long newRoid = lowLevelInterface.commitTransaction(tid, "Moved opening to x=1500, z=800", true);
		IfcModelInterface newModel = bimServerClient.getModel(project, newRoid, true, true, true);
		IfcOpeningElement newOpening = newModel.getFirst(IfcOpeningElement.class);
		IfcCartesianPoint newOpeningLocation = ((IfcAxis2Placement3D)((IfcLocalPlacement) newOpening.getObjectPlacement()).getRelativePlacement()).getLocation();
		Assert.assertEquals("Unexpected updated coordinates", newOpeningLocation.getCoordinates(), Arrays.asList(1500.,0.,800.));
		Assert.assertEquals("opening OID should not change", opening.getOid(), newOpening.getOid());
		Assert.assertNotEquals("opening RID should change", opening.getRid(), newOpening.getRid());  // new geometry assigned
		// Assert.assertEquals("placement RID should not change", opening.getObjectPlacement().getRid(), newOpening.getObjectPlacement().getRid());
		Assert.assertEquals("location OID should not change", openingLocation.getOid(), newOpeningLocation.getOid());
		Assert.assertNotEquals("location RID should change", openingLocation.getRid(), newOpeningLocation.getRid()); // coordinates changed
		Assert.assertNotEquals("geometry OID should change", opening.getGeometry().getOid(), newOpening.getGeometry().getOid());
	}

	@Test
	public void testChangeAffectingOneProduct() throws ServerException, UserException, BimServerClientException {
		// change window width: only window will have to be regenerated
		SProject project = serviceInterface.getProjectByPoid(poid);
		SRevision revision1 = serviceInterface.getRevision(project.getLastRevisionId());
		IfcModelInterface model = bimServerClient.getModel(project, revision1.getOid(), true, true, true);
		IfcWindow window = model.getFirst(IfcWindow.class);
		long tid = lowLevelInterface.startTransaction(poid);
		IfcCartesianPoint location = ((IfcAxis2Placement3D)((IfcLocalPlacement) window.getObjectPlacement()).getRelativePlacement()).getLocation();
		Assert.assertEquals(50, location.getCoordinates().get(1), 0.0001);
		lowLevelInterface.setDoubleAttributeAtIndex(tid, location.getOid(), "Coordinates", 1, 125.);
		for (IfcRepresentation representation : window.getRepresentation().getRepresentations()){
			if("".equals(representation.getRepresentationIdentifier())) {
				IfcExtrudedAreaSolid representation3d = (IfcExtrudedAreaSolid) representation.getItems().get(0);
				List<IfcCartesianPoint> points = ((IfcPolyline)((IfcArbitraryClosedProfileDef) representation3d.getSweptArea()).getOuterCurve()).getPoints();
				Assert.assertEquals(200, points.get(1).getCoordinates().get(1), 0.0001);
				Assert.assertEquals(200, points.get(2).getCoordinates().get(1), 0.0001);
				lowLevelInterface.setDoubleAttributeAtIndex(tid, points.get(1).getOid(), "Coordinates", 1, 50.);
				lowLevelInterface.setDoubleAttributeAtIndex(tid, points.get(2).getOid(), "Coordinates", 1, 50.);
			}
		}
		long newRoid = lowLevelInterface.commitTransaction(tid, "Window depth changed from 200 to 50, centered", false);
		IfcModelInterface newModel = bimServerClient.getModel(project, newRoid, true, true, true);
		IfcWindow newWindow = newModel.getFirst(IfcWindow.class);
		long topicId = serviceInterface.regenerateGeometryByOid(newRoid, renderEngineOid, newWindow.getOid()); // window oid should not have changed
		waitForLongActionFinished(topicId);
		// TODO check OIDs and RIDs
	}

	@Test
	public void testLowLevelNoGeomChangeAfterRegenerate() throws ServiceException, BimServerClientException {
		SProject project = serviceInterface.getProjectByPoid(poid);
		SRevision revision1 = serviceInterface.getRevision(project.getLastRevisionId());

		// regenerate geometry for first revision
		long topic = serviceInterface.regenerateGeometry(project.getLastRevisionId(), renderEngineOid);
		waitForLongActionFinished(topic);
		revision1 = serviceInterface.getRevision(revision1.getOid());
		int nrTriangles1 = countPrimitives(bimServerClient, project, revision1.getOid());
		Assert.assertEquals("Expecting no new revision, 1 total.", 1, project.getRevisions().size());
		Assert.assertTrue(revision1.isHasGeometry());
		Assert.assertEquals("Expecting 2 new geometry reports, 4 total.", 4, revision1.getExtendedData().size());
		Assert.assertEquals(56, revision1.getNrPrimitives());
		Assert.assertEquals(56, nrTriangles1);

		// new revision from low level call without geometry regeneration
		long tid = lowLevelInterface.startTransaction(project.getOid());
		lowLevelInterface.commitTransaction(tid, "done nothing", false);
		project = serviceInterface.getProjectByPoid(project.getOid());
		SRevision revision2 = serviceInterface.getRevision(project.getLastRevisionId());
		int nrTriangles2 = countPrimitives(bimServerClient, project, revision2.getOid());
		Assert.assertEquals("Expecting one new revision, 2 total.", 2, project.getRevisions().size());
		Assert.assertTrue(revision2.isHasGeometry());
		Assert.assertEquals("Revision 2 should have same number of primitives as revision 1", revision1.getNrPrimitives(), revision2.getNrPrimitives());
		Assert.assertEquals("Revision 2 should have same number of primitives as revision 1", 56, nrTriangles2);
		Assert.assertEquals("Expecting no new geometry reports, 0 total.", 0, revision2.getExtendedData().size());
	}

	private void waitForLongActionFinished(long topic) throws UserException, ServerException {
		for(int i=0; i<10; i++){
			SLongActionState progress = bimServerClient.getNotificationRegistryInterface().getProgress(topic);
			if(progress != null){
				if(progress.getState().equals(SActionState.FINISHED))
					break;
				else if(progress.getState().equals(SActionState.AS_ERROR))
					fail(String.join("\n", progress.getErrors()));
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private int countPrimitives(BimServerClientInterface bimServerClient, SProject project, long roid) throws BimServerClientException, UserException, ServerException {
		IfcModelInterface model = bimServerClient.getModel(project, roid, true, true, true);
		return model.getAllWithSubTypes(IfcProduct.class).stream().mapToInt(ifcProduct ->
			ifcProduct.getGeometry() == null ? 0 : ifcProduct.getGeometry().getPrimitiveCount()
		).reduce(Integer::sum).orElse(0);
	}
}