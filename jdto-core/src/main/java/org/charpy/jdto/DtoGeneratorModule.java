package org.charpy.jdto;

import java.io.File;
import java.io.IOException;

import org.charpy.jdto.annotations.DtoField;
import org.charpy.jdto.annotations.GenerateDto;
import org.charpy.jdto.annotations.IncludeMethodToDTO;
import org.charpy.jdto.annotations.MethodAccessModifier;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JMod;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotatedElement;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaSource;

/**
 * Module that process code and generate DTO
 */
public class DtoGeneratorModule {

	// JCodeModel to generate the output source code
	private final JCodeModel jcodeModel;
	private String generationToken = "DTO";
	private TokenPosition tokenPosition = TokenPosition.POSTFIX;
	private String outputPackage;

	public DtoGeneratorModule() throws Exception {
		jcodeModel = new JCodeModel();
	}

	/**
	 * Process all the java files recursively and generate the output source files in the output folder specified
	 * @param sourceFolder
	 * @param outputFolder
	     * @param outputPackage
	 * @throws ClassNotFoundException
	 * @throws JClassAlreadyExistsException
	 * @throws IOException
	 */
	public void process(File sourceFolder, File outputFolder, String outputPackage) throws ClassNotFoundException, JClassAlreadyExistsException, IOException {
		JavaProjectBuilder builder = new JavaProjectBuilder();
		builder.addSourceTree(sourceFolder);
		this.outputPackage = outputPackage;
		for (JavaSource js : builder.getSources()) {
			for (JavaClass javaClass : js.getClasses()) {
				processJavaClass(javaClass, outputFolder);
			}
		}
		outputFolder.mkdirs();
		jcodeModel.build(outputFolder);
	}

	/**
	 * Process a source java class and generate the output DTO file 
	 * @param jc
	 * @param outputFolder
	 * @throws JClassAlreadyExistsException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public void processJavaClass(JavaClass jc, File outputFolder) throws JClassAlreadyExistsException, ClassNotFoundException, IOException {
		JavaAnnotation classAnnotation = getAnnotationFromEntity(GenerateDto.class, jc);
		if (classAnnotation != null) {

			// Iterate over fields and generate getter/setter for those with annotation DtoField
			DtoWriter writer = null;
			for (JavaField field : jc.getFields()) {
				JavaAnnotation fieldAnno = getAnnotationFromEntity(DtoField.class, field);

				if (fieldAnno != null) {
					//TODO FIX ME - What happens if there are no fields but there are annotated methods??
					if (writer == null) {
						if (tokenPosition == TokenPosition.POSTFIX) {
							writer = new DtoWriter(jcodeModel, outputPackage + "." + jc.getName() + generationToken);
						} else {
							writer = new DtoWriter(jcodeModel, outputPackage + "." + generationToken + jc.getName());
						}
					}

					boolean getter = true;
					MethodAccessModifier getterModifier = MethodAccessModifier.Public;
					boolean setter = true;
					MethodAccessModifier setterModifier = MethodAccessModifier.Public;
					MethodAccessModifier fieldModifier = MethodAccessModifier.Private;

					Object fieldModifierObj = fieldAnno.getNamedParameter("fieldModifier");
					if (fieldModifierObj != null)
						fieldModifier = MethodAccessModifier.fromQDoxAnnotationString(fieldModifierObj.toString());

					//Getting getter
					Object getterObj = fieldAnno.getNamedParameter("getter");
					if (getterObj != null)
						getter = Boolean.parseBoolean(getterObj.toString());

					//Getting getter modifier
					Object getterModifierObj = fieldAnno.getNamedParameter("getterModifier");
					if (getterModifierObj != null)
						getterModifier =  MethodAccessModifier.fromQDoxAnnotationString(getterModifierObj.toString());

					//Getting setter
					Object setterObj = fieldAnno.getNamedParameter("setter");
					if (setterObj != null)
						setter = Boolean.parseBoolean(setterObj.toString());

					//Getting setter modifier
					Object setterModifierObj = fieldAnno.getNamedParameter("setterModifier");
					if (setterModifierObj != null)
						setterModifier =  MethodAccessModifier.fromQDoxAnnotationString(setterModifierObj.toString());

					writer.addField(jcodeModel.parseType(field.getType().getGenericCanonicalName()), field.getName(), fieldModifier.getJModConstant(), getter, getterModifier.getJModConstant(), setter, setterModifier.getJModConstant());
				}
			}

			// Lets see which method need to be included
			if (jc.getMethods() != null && !jc.getMethods().isEmpty()) {
				for (JavaMethod m : jc.getMethods()) {
					JavaAnnotation methodAnno = getAnnotationFromEntity(IncludeMethodToDTO.class, m);
					if (methodAnno != null) {
						writer.addMethod(m);
					}
				}
			}

			// Write dto to disk
			if (writer != null) {
				writer.build(outputFolder);
			}
		}
	}

	private JavaAnnotation getAnnotationFromEntity(Class<?> class1, JavaAnnotatedElement e) {
		for (JavaAnnotation annotation : e.getAnnotations()) {
			if (annotation.getType().getFullyQualifiedName().equals(class1.getName())) {
				return annotation;
			}
		}
		return null;
	}

	public void setGenerationToken(String generationToken) {
		this.generationToken = generationToken;
	}

	public void setTokenPosition(TokenPosition tokenPosition) {
		this.tokenPosition = tokenPosition;
	}

}
