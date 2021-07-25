package personthecat.overwritevalidator.processors;

import org.gradle.api.Project;
import personthecat.overwritevalidator.CtUtils;
import personthecat.overwritevalidator.LauncherContext;
import spoon.Launcher;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManualImportProcessor {

    /** Representing all possible imports in a Java source file.. */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?(.*\\.(\\w+)).*;.*$");

    /** Representing the package declaration at the top of the file. */
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package.*$", Pattern.MULTILINE);

    /**
     * This method is responsible for correcting a series of import-related errors
     * that will occur when serializing files through Spoon.
     * <p>
     *   Namely, collapsed imports, static imports, and nested type imports, which
     *   are all misprinted by the library.
     * </p>
     *
     * @param project The current project which the plugin has been applied to.
     * @param launcher The context storing the parsed AST of this project.
     */
    public static void fixImports(final Project project, final Launcher launcher) {
        final Set<File> javaSources = LauncherContext.getMainSourceSet(project);
        final File generatedSources = launcher.getEnvironment().getSourceOutputDirectory();
        for (final CtType<?> type : CtUtils.getAllClasses(launcher.getModel())) {
            final CtType<?> overwritten = LauncherContext.getOverwrittenClass(type);
            final File typeFile = type.getPosition().getFile();
            final File generated = new File(generatedSources, getRelativePath(javaSources, typeFile));
            if (overwritten != null && generated.exists()) {
                fixClassFile(type, generated, overwritten.getPosition().getFile());
            } else {
                copyFile(typeFile, generated);
            }
        }
    }

    private static String getRelativePath(final Set<File> sources, final File f) {
        final String filePath = f.getPath();
        for (final File source : sources) {
            final String sourcePath = source.getPath();
            if (filePath.startsWith(sourcePath)) {
                return filePath.substring(sourcePath.length());
            }
        }
        throw new IllegalStateException("No matching source: " + filePath);
    }

    private static void fixClassFile(final CtType<?> type, final File generated, final File common) {
        final List<String> lines = readLines(generated);
        final Set<ImportData> generatedImports = getImports(lines);
        final String content = removeImports(generatedImports, String.join("\n", lines));

        final Set<ImportData> imports = getImports(readLines(common));
        imports.addAll(generatedImports);

        writeFile(generated, addImports(imports, removePaths(type, imports, content)));
    }

    private static List<String> readLines(final File f) {
        try {
            return Files.readAllLines(f.toPath());
        } catch (final IOException e) {
            throw new UncheckedIOException("Fixing imports", e);
        }
    }

    private static Set<ImportData> getImports(final List<String> lines) {
        final Set<ImportData> imports = new HashSet<>();
        for (final String line : lines) {
            final Matcher m = IMPORT_PATTERN.matcher(line);
            if (m.matches()) {
                imports.add(new ImportData(m.group(0), m.group(1), m.group(2)));
            }
        }
        return imports;
    }

    private static String removeImports(final Set<ImportData> data, String content) {
        for (final ImportData i : data) {
            content = content.replace(i.statement, "");
        }
        return content;
    }

    private static String removePaths(final CtType<?> type, final Set<ImportData> data, String content) {
        for (final ImportData i : data) {
            content = content.replace(i.path, i.reference);
        }
        return content.replace(type.getPackage().getQualifiedName() + ".", "")
            .replace("java.lang.", "");
    }

    private static String addImports(final Set<ImportData> data, final String content) {
        final int index = getPackageIndex(content) + 1;
        final StringBuilder sb = new StringBuilder(content.substring(0, index));

        for (final ImportData i : data) {
            sb.append(i.statement);
            sb.append(System.lineSeparator());
        }
        return sb.append(content.substring(index)).toString();
    }

    private static int getPackageIndex(final String content) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("No package declaration in Java file.");
        }
        return matcher.end();
    }

    private static void writeFile(final File f, final String content) {
        try {
            Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException("Fixing imports", e);
        }
    }

    private static void copyFile(final File source, final File destination) {
        final File parent = destination.getParentFile();
        if (!(parent.exists() || parent.mkdirs())) {
            throw new IllegalStateException("Creating folder");
        }
        try {
            Files.copy(source.toPath(), destination.toPath());
        } catch (final IOException e) {
            throw new UncheckedIOException("Copying file", e);
        }
    }

    private static class ImportData {
        final String statement;
        final String path;
        final String reference;

        ImportData(final String statement, final String path, final String reference) {
            this.statement = statement;
            this.path = path;
            this.reference = reference;
        }

        @Override
        public int hashCode() {
            return this.reference.hashCode();
        }
    }
}
