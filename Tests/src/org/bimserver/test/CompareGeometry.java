package org.bimserver.test;

import com.google.common.collect.Sets;
import org.bimserver.LocalDevSetup;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.geometry.GeometryData;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.plugins.services.Geometry;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.junit.Assert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompareGeometry {
    public static void main(String[] args) throws ServerException, UserException, BimServerClientException {
        BimServerClientInterface client = LocalDevSetup.setupJson("http://localhost:8080");
        long poid = 131073;
        SProject project = client.getServiceInterface().getProjectByPoid(poid);
        List<Long> revisions = project.getRevisions();
        Assert.assertTrue(revisions.size()>=2);
        Long last = revisions.get(revisions.size() - 1);
        Long beforeLast = revisions.get(revisions.size() - 2);
        IfcModelInterface model1 = client.getModel(project, beforeLast, true, false, true);
        IfcModelInterface model2 = client.getModel(project, last, true, false, true);
        Set<Long> oids1 = new HashSet<>();
        Set<Long> oids2 = new HashSet<>();
        for (IfcProduct prod : model1.getAllWithSubTypes(IfcProduct.class)) {
            oids1.add(prod.getOid());
        }
        for (IfcProduct prod : model2.getAllWithSubTypes(IfcProduct.class)) {
            oids2.add(prod.getOid());
        }
        if (oids1.equals(oids2)) {
            for (Long commonOid : oids1) {
                GeometryData geomData1 = ((IfcProduct)model1.get(commonOid)).getGeometry().getData();
                GeometryData geomData2 = ((IfcProduct)model2.get(commonOid)).getGeometry().getData();
                Arrays.equals(geomData1.getVertices().getData(), geomData2.getVertices().getData());
                Arrays.equals(geomData1.getIndices().getData(), geomData2.getIndices().getData());
                Arrays.equals(geomData1.getNormals().getData(), geomData2.getNormals().getData());
                /*
                Geometry geom1 = client.getGeometry(beforeLast, model1.get(commonOid));
                Geometry geom2 = client.getGeometry(last, model2.get(commonOid));
                geom1.getVertices().equals(geom2.getVertices());
                geom1.getIndices().equals(geom2.getIndices());
                geom1.getNormals().equals(geom2.getNormals());
                */
                // they must be the same?!
            }
        } else {
            Sets.difference(oids1, oids2); // new in model 1
            Sets.difference(oids1, oids2); // new in model 1
        }
        for (Long newOids1 : Sets.intersection(oids1, oids2)) {
        }
        for (Long newOids2 : Sets.intersection(oids2, oids1)) {
        }


    }
}
