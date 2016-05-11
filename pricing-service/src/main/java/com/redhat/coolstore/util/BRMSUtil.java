package com.redhat.coolstore.util;

import java.io.Serializable;

import javax.inject.Singleton;

import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;

@Singleton
public class BRMSUtil implements Serializable {

	private static final long serialVersionUID = 1562882558996412866L;

	private KieContainer kContainer = null;
    
    public BRMSUtil() {	    
    	
    	KieServices kServices = KieServices.Factory.get();

			ReleaseId releaseId = kServices.newReleaseId( "com.redhat", "coolstore", "LATEST" );

			kContainer = kServices.newKieContainer( releaseId );

			KieScanner kScanner = kServices.newKieScanner( kContainer );


			// Start the KieScanner polling the maven repository every 10 seconds
			System.out.println("Starting KieScanner...");
			System.out.println();
			kScanner.start( 10000L );
			System.out.println("Started KieScanner sucessfully...");
			System.out.println();
    }


    
    public StatelessKieSession getStatelessSession() {

        return kContainer.newStatelessKieSession();

    }

    /*
     * KieSession is the new StatefulKnowledgeSession from BRMS 5.3.
     */
    public KieSession getStatefulSession() {

        return kContainer.newKieSession();

    }

}
