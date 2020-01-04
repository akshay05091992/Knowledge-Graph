package resttest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

//host:8080/restproj/v1/resource

@ApplicationPath("v1")
public class AppConfig extends Application{
	private Set<Class<?>> resources=new HashSet<>();
	
	public AppConfig() {
		System.out.println("Created AppConfig");
		resources.add(Resource1.class);
	}

	@Override
	public Set<Class<?>> getClasses() {
		// TODO Auto-generated method stub
		return resources;
	}

	
}
