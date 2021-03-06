package fr.lteconsulting.autothreaded;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes( AutoThreadedProcessor.AnnotationFqn )
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AutoThreadedProcessor extends AbstractProcessor
{
	public final static String AnnotationFqn = "fr.lteconsulting.autothreaded.AutoThreaded";

	@Override
	public boolean process( Set<? extends TypeElement> annotations, RoundEnvironment roundEnv )
	{
		for( TypeElement element : ElementFilter.typesIn( roundEnv.getElementsAnnotatedWith( processingEnv.getElementUtils().getTypeElement( AnnotationFqn ) ) ) )
		{
			if( element.getKind() == ElementKind.INTERFACE )
				processType( element );
			else
				processClass( element );
		}

		return true;
	}

	private void processType( TypeElement element )
	{
		String template = readResource( "fr/lteconsulting/autothreaded/AutoThreaded.txt" );

		// le type est une interface.

		String packageName = ((PackageElement) element.getEnclosingElement()).getQualifiedName().toString();
		String autothreadedName = element.getSimpleName() + "AutoThreaded";

		// ecrire la clase qui l'implémente
		StringBuilder delegate = new StringBuilder();
		StringBuilder methods = new StringBuilder();

		int id = -1;
		for( Element child : element.getEnclosedElements() )
		{
			if( child.getKind() != ElementKind.METHOD )
				continue;

			ExecutableElement method = (ExecutableElement) child;

			if( !child.getModifiers().contains( Modifier.PUBLIC ) )
				continue;

			id++;

			String returnTypeFqn = method.getReturnType().toString();

			delegate.append( "            case " + id + ":\n" );
			delegate.append( "                return implementation." + method.getSimpleName() + "( " );
			int pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi > 0 )
					delegate.append( ", " );
				delegate.append( "(" + p.asType() + ") message.getParameters()[" + pi + "]" );
				pi++;
			}
			delegate.append( " );\n" );

			methods.append( "        @SuppressWarnings( \"unchecked\" )" );
			methods.append( "        @Override\n" );
			methods.append( "        public " + returnTypeFqn + " " + method.getSimpleName() + "(" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.asType() + " " + p.getSimpleName() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( ")\n" );
			methods.append( "        {\n" );
			methods.append( "            return (" + returnTypeFqn + ") sendMessage( new AutoThreadMessage( " + id + ", new Object[] {" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.getSimpleName().toString() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( "} ) );\n" );
			methods.append( "	    }\n\n" );
		}

		template = template.replaceAll( "PACKAGE", packageName );
		template = template.replaceAll( "CLASS_NAME", autothreadedName );
		template = template.replaceAll( "INTERFACE", element.getSimpleName().toString() );
		template = template.replaceAll( "DELEGATE", delegate.toString() );
		template = template.replaceAll( "METHODS", methods.toString() );

		try
		{
			JavaFileObject jfo = processingEnv.getFiler().createSourceFile( packageName + "." + autothreadedName, element );

			OutputStream os = jfo.openOutputStream();
			PrintWriter pw = new PrintWriter( os );
			pw.print( template );
			pw.close();
			os.close();

			processingEnv.getMessager().printMessage( Kind.NOTE, "AutoThreaded généré !", element );
		}
		catch( IOException e )
		{
			e.printStackTrace();
			processingEnv.getMessager().printMessage( Kind.ERROR, "AutoThreaded non généré !" + e, element );
		}

	}

	private void processClass( TypeElement element )
	{
		String template = readResource( "fr/lteconsulting/autothreaded/AutoThreadedClass.txt" );

		// le type est une classe.

		String packageName = ((PackageElement) element.getEnclosingElement()).getQualifiedName().toString();
		String autothreadedName = element.getSimpleName() + "AutoThreaded";

		// ecrire la clase qui en hérite
		StringBuilder constructor = new StringBuilder();
		StringBuilder delegate = new StringBuilder();
		StringBuilder methods = new StringBuilder();
		StringBuilder loopEntry = new StringBuilder();
		
		int id = -1;
		for( Element child : element.getEnclosedElements() )
		{
			if( child.getKind() != ElementKind.METHOD )
				continue;

			ExecutableElement method = (ExecutableElement) child;

			if( "onEmptyMessageQueue".equals( method.getSimpleName().toString() ) && method.getModifiers().contains( Modifier.PROTECTED ) )
			{
				processingEnv.getMessager().printMessage( Kind.NOTE, "This method will be called on loop entry", child );
				loopEntry.append( "                    " + autothreadedName + ".this.onEmptyMessageQueue();" );
				
				constructor.append( " true " );
				
				continue;
			}

			if( !child.getModifiers().contains( Modifier.PUBLIC ) )
				continue;

			id++;

			String returnTypeFqn = method.getReturnType().toString();

			int pi = 0;

			/**
			 * Délégation à on_XXX
			 */
			delegate.append( "                                case " + id + ":\n" );
			if( "void".equals( returnTypeFqn ) )
			{
				delegate.append( "                                        on_" + method.getSimpleName() + "( " );
				pi = 0;
				for( VariableElement p : method.getParameters() )
				{
					if( pi > 0 )
						delegate.append( ", " );
					delegate.append( "(" + p.asType() + ") message.getParameters()[" + pi + "]" );
					pi++;
				}
				delegate.append( " );\n" );
				delegate.append( "                                        return null;\n" );
			}
			else
			{
				delegate.append( "                                        return on_" + method.getSimpleName() + "( " );
				pi = 0;
				for( VariableElement p : method.getParameters() )
				{
					if( pi > 0 )
						delegate.append( ", " );
					delegate.append( "(" + p.asType() + ") message.getParameters()[" + pi + "]" );
					pi++;
				}
				delegate.append( " );\n" );
			}
			delegate.append( "\n" );

			/**
			 * Méthode XXX de l'interface
			 */
			methods.append( "    @Override\n" );
			methods.append( "    public " + returnTypeFqn + " " + method.getSimpleName() + "(" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.asType() + " " + p.getSimpleName() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( ")\n" );
			methods.append( "    {\n" );
			if( "void".equals( returnTypeFqn ) )
				methods.append( "        autothreaded.sendMessage( new AutoThreadMessage( " + id + ", new Object[] {" );
			else
				methods.append( "        return (" + returnTypeFqn + ") autothreaded.sendMessage( new AutoThreadMessage( " + id + ", new Object[] {" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.getSimpleName().toString() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( "} ) );\n" );
			methods.append( "    }\n\n" );

			/**
			 * Méthode asynchrone XXX de l'interface
			 */
			methods.append( "    @SuppressWarnings(\"unchecked\")\n" );
			if( "void".equals( returnTypeFqn ) )
				methods.append( "    public Future<Void> " + method.getSimpleName() + "Async(" );
			else
				methods.append( "    public Future<" + returnTypeFqn + "> " + method.getSimpleName() + "Async(" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.asType() + " " + p.getSimpleName() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( ")\n" );
			methods.append( "    {\n" );
			if( "void".equals( returnTypeFqn ) )
				methods.append( "        return (Future<Void>)(Future<?>) autothreaded.postMessage( new AutoThreadMessage( " + id + ", new Object[] {" );
			else
				methods.append( "        return (Future<" + returnTypeFqn + ">)(Future<?>) autothreaded.postMessage( new AutoThreadMessage( " + id + ", new Object[] {" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.getSimpleName().toString() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( "} ) );\n" );
			methods.append( "    }\n\n" );

			/**
			 * Méthode on_XXX
			 */
			methods.append( "    private " + returnTypeFqn + " on_" + method.getSimpleName() + "(" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.asType() + " " + p.getSimpleName() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( ")\n" );
			methods.append( "    {\n" );
			if( "void".equals( returnTypeFqn ) )
				methods.append( "        super." + method.getSimpleName() + "(" );
			else
				methods.append( "        return super." + method.getSimpleName() + "(" );
			pi = 0;
			for( VariableElement p : method.getParameters() )
			{
				if( pi == 0 )
					methods.append( " " );
				if( pi > 0 )
					methods.append( ", " );
				methods.append( p.getSimpleName().toString() );
				pi++;
			}
			if( pi > 0 )
				methods.append( " " );
			methods.append( ");\n" );
			methods.append( "    }\n\n" );
		}

		template = template.replaceAll( "CONSTRUCTOR", constructor.toString() );
		template = template.replaceAll( "PACKAGE", packageName );
		template = template.replaceAll( "BASE_CLASS_NAME", element.getSimpleName().toString() );
		template = template.replaceAll( "CLASS_NAME", autothreadedName );
		template = template.replaceAll( "DELEGATE", delegate.toString() );
		template = template.replaceAll( "METHODS", methods.toString() );
		template = template.replaceAll( "EMPTY_QUEUE", loopEntry.toString() );

		try
		{
			JavaFileObject jfo = processingEnv.getFiler().createSourceFile( packageName + "." + autothreadedName, element );

			OutputStream os = jfo.openOutputStream();
			PrintWriter pw = new PrintWriter( os );
			pw.print( template );
			pw.close();
			os.close();

			processingEnv.getMessager().printMessage( Kind.MANDATORY_WARNING, "AutoThreaded généré : " + autothreadedName, element );
		}
		catch( IOException e )
		{
			e.printStackTrace();
			processingEnv.getMessager().printMessage( Kind.ERROR, "AutoThreaded non généré !" + e, element );
		}

	}

	private static String readResource( String fqn )
	{
		try
		{
			return new Scanner( AutoThreadedProcessor.class.getClassLoader().getResourceAsStream( fqn ) ).useDelimiter( "\\A" ).next();
		}
		catch( Exception e )
		{
			return null;
		}
	}
}
