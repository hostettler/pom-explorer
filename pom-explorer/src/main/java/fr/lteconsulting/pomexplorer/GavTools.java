package fr.lteconsulting.pomexplorer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GavTools
{
	public static List<String> analyseProvidedClasses( WorkingSession session, Gav gav, ILogger log )
	{
		log.html( "<br/><b>Java classes provided by gav " + gav + "</b> :<br/>" );

		MavenResolver resolver = session.mavenResolver();

		File resolvedFile = resolver.resolvePom( gav, "jar" );
		if( resolvedFile == null )
		{
			log.html( Tools.warningMessage( "cannot resolve the gav " + gav ) );
			return null;
		}

		log.html( "resolved file : " + resolvedFile.getAbsolutePath() + "<br/>" );

		try
		{
			List<String> classNames = new ArrayList<String>();
			ZipInputStream zip = new ZipInputStream( new FileInputStream( resolvedFile ) );
			for( ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry() )
			{
				System.out.println( entry.getName() );
				if( !entry.isDirectory() && entry.getName().endsWith( ".class" ) )
				{
					String className = entry.getName().replace( '/', '.' );
					classNames.add( className.substring( 0, className.length() - ".class".length() ) );
				}
			}
			zip.close();

			Collections.sort( classNames );

			return classNames;
		}
		catch( Exception e )
		{
			log.html( Tools.errorMessage( "error during file inspection ! " + e.getMessage() ) );
			return null;
		}
	}
}
