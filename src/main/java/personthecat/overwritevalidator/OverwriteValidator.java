package personthecat.overwritevalidator;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Comparator;

public class OverwriteValidator implements Plugin<Project> {

    @Override
    public void apply(@Nonnull final Project project) {
        if (LauncherContext.getMainSourceSet(project).isEmpty()) {
            System.out.println("No source directories " + project.getName() + ". Skipping.");
            return;
        }
        OverwriteValidatorExtension.create(project);
        getCompileJava(project).doFirst(t -> runLauncher(project, t));
    }


    private static Task getCompileJava(final Project project) {
        for (final Task task : project.getTasks()) {
            if ("compileJava".equals(task.getName())) {
                return task;
            }
        }
        throw new NullPointerException("No compileJava task in project");
    }

    private static void runLauncher(final Project project, final Task compileJava) {
        final OverwriteValidatorExtension config = OverwriteValidatorExtension.get(project);
        if (!project.equals(config.getCommonProject())) {
            deleteDirectory(config.getOutputDirectory());
            LauncherContext.initStatic(project);
            LauncherContext.process(project);
            if (config.generateCode()) {
                compileJava.setProperty("source", config.getOutputDirectory());
            }
        }
    }

    private static void deleteDirectory(final File dir) {
        if (!dir.exists()) {
            return;
        }
        try {
            Files.walk(dir.toPath()).sorted(Comparator.reverseOrder()).forEach(p -> {
                if (!p.toFile().delete()) {
                    System.err.println("Error deleting " + p);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException("Deleting files", e);
        }
    }
}
