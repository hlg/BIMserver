package org.bimserver.test;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.emf.Schema;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.ifc4.Ifc4Package;
import org.bimserver.shared.ChannelConnectionException;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.*;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ImplicitInverses {
    BimServerClient client;

    public static void main (String[] args) throws BimServerClientException, ServiceException, ChannelConnectionException, MalformedURLException {
        new ImplicitInverses().schema();
    }
    private void schema(){
        PackageMetaData ifc4 = new PackageMetaData(Ifc4Package.eINSTANCE, Schema.IFC4, Paths.get("temp"));
        ifc4.getAllClasses().forEach(c -> System.out.println(c.getName()));
        System.out.println(ifc4.getAllClasses().size());
        System.out.println(ifc4.getAllEClassesThatHaveInverses().size());
        Supplier<Stream<EReference>> refSupplier = () -> ifc4.getEPackage().getEClassifiers().stream()
                .filter(EClass.class::isInstance).map(EClass.class::cast)
                .flatMap(c -> c.getEReferences().stream().filter(r -> r.getEAnnotation("hidden")==null && !"List".equals(r.getName()))
                );
        Supplier<Stream<EAttribute>> attrSupplier = () -> ifc4.getEPackage().getEClassifiers().stream()
                .filter(EClass.class::isInstance).map(EClass.class::cast)
                .flatMap(c -> c.getEAttributes().stream().filter(a -> a.getEAnnotation("hidden")==null)
                );
        long all = refSupplier.get().count();
        System.out.println(all);
        long attrs = attrSupplier.get().count();
        System.out.println(attrs);
        refSupplier.get().forEach(r -> {
            System.out.println( r.getContainerClass().getName() + "." + r.getName() + " / " + ifc4.isInverse(r));
        });
        long directInv = refSupplier.get().filter(ifc4::isInverse).count();
        System.out.println("refs: " + all + ", direct inv: "+ directInv);
    }

    private void start() throws ServiceException, MalformedURLException, BimServerClientException, ChannelConnectionException {
        client = new JsonBimServerClientFactory("http://localhost:8082").create(
                new UsernamePasswordAuthenticationInfo("admin@localhost", "admin")
        );
        SProject project = client.getServiceInterface().getProjectByPoid(131073L);
        IfcModelInterface model = client.getModel(project, project.getLastRevisionId(), false, true);
        IdEObject first = model.getAllWithSubTypes(model.getPackageMetaData().getEClass("IfcRepresentation")).get(0);
        first.eClass().getEAllReferences().forEach(e -> {
            boolean explInv = model.getPackageMetaData().isInverse(e);
            boolean hasInv = model.getPackageMetaData().hasInverse(e);
            // EReference inv = model.getPackageMetaData().getInverseOrOpposite(first.eClass(), e);
            EReference inv = e.getEOpposite();
            System.out.println(e.getName()+": "+(hasInv?"hasInv, ":"hasNoInv, ")+ (explInv?"isInv, ":"isNoInv, ") + (inv==null ? "-" : inv.getEContainingClass().getName() + "." + inv.getName()));
        });


        client.disconnect();
    }

    private void checkin() throws ServerException, UserException, MalformedURLException {
        long poid = client.getServiceInterface().addProject("test" + Math.random(), "ifc4").getOid();
        // long poid = client.getServiceInterface().getAllProjectsSmall().get(0).getOid();
        long deserializerID = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", poid).getOid();
        long roid = client.checkinSync(poid, "test", deserializerID, false, new URL("https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/annex/annex-e/wall-with-opening-and-window.ifc")).getRoid();
        System.out.println(poid);
        System.out.println(roid);
    }
}
