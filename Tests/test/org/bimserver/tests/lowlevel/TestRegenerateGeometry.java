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
import org.bimserver.models.ifc4.IfcProduct;
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
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.fail;

public class TestRegenerateGeometry extends TestWithEmbeddedServer {

	private BimServerClientInterface bimServerClient;

	@Test
	public void testLowLevelNoGeomChangeAfterRegenerate() throws ServiceException, ChannelConnectionException, BimServerClientException, MalformedURLException {
		// setup and checkin
		bimServerClient = getFactory().create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));
		LowLevelInterface lowLevelInterface = bimServerClient.getLowLevelInterface();
		ServiceInterface serviceInterface = bimServerClient.getServiceInterface();
		SProject project = serviceInterface.addProject("test" + Math.random(), "ifc4");
		SDeserializerPluginConfiguration deserializer = serviceInterface.getSuggestedDeserializerForExtension("ifc", project.getOid());
		bimServerClient.checkinSync(project.getOid(), "test", deserializer.getOid(), false, new URL("https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/annex/annex-e/wall-with-opening-and-window.ifc")); // TODO add to test data repo
		project = refreshProject(bimServerClient, project);
		SRevision revision1 = serviceInterface.getRevision(project.getLastRevisionId());
		long renderEngineOid = bimServerClient.getPluginInterface().getDefaultRenderEngine().getOid();

		// regenerate geometry for first revision
		long topic = serviceInterface.regenerateGeometry(project.getLastRevisionId(), renderEngineOid);
		waitForLongActionFinished(topic);
		revision1 = refreshRevision(bimServerClient, revision1);
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

	private SRevision refreshRevision(BimServerClientInterface bimServerClient, SRevision revision1) throws ServerException, UserException {
		return bimServerClient.getServiceInterface().getRevision(revision1.getOid());
	}

	private SProject refreshProject(BimServerClientInterface bimServerClient, SProject project) throws ServerException, UserException {
		return bimServerClient.getServiceInterface().getProjectByPoid(project.getOid());
	}

	private int countPrimitives(BimServerClientInterface bimServerClient, SProject project, long roid) throws BimServerClientException, UserException, ServerException {
		IfcModelInterface model = bimServerClient.getModel(project, roid, true, true, true);
		return model.getAllWithSubTypes(IfcProduct.class).stream().mapToInt(ifcProduct ->
			ifcProduct.getGeometry() == null ? 0 : ifcProduct.getGeometry().getPrimitiveCount()
		).reduce(Integer::sum).orElse(0);
	}
}