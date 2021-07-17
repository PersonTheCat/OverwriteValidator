package personthecat.overwritevalidator;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import personthecat.overwritevalidator.processors.InheritMissingMembersProcessor;
import personthecat.overwritevalidator.processors.InheritProcessor;
import personthecat.overwritevalidator.processors.ManualImportProcessor;
import personthecat.overwritevalidator.processors.MissingOverwriteProcessor;
import personthecat.overwritevalidator.processors.OverwriteClassProcessor;
import personthecat.overwritevalidator.processors.OverwriteProcessor;
import personthecat.overwritevalidator.processors.OverwriteTargetProcessor;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.FileSystemFolder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class LauncherContext {

    /** Stores the AST of the common project. Avoids redundant parsing. */
    private static final AtomicReference<Cache> DATA = new AtomicReference<>();

    /** A message to display when the cache has not loaded in time. */
    private static final String OUT_OF_ORDER = "Plugins ran out of order";

    private LauncherContext() {}

    public static synchronized void initStatic(final Project project) {
        if (DATA.get() == null) {
            final OverwriteValidatorExtension config = OverwriteValidatorExtension.get(project);
            DATA.set(new Cache(createCommonModel(config.getCommonProject())));
        }
    }

    @Nullable
    public static CtType<?> getOverwrittenClass(final CtType<?> ctClass) {
        for (final CtType<?> t : LauncherContext.getCommonClasses()) {
            if (t.getQualifiedName().equals(ctClass.getQualifiedName())) {
                return t;
            }
        }
        return null;
    }

    @Nonnull
    public static CtType<?> getOverwrittenClassOrThrow(final CtType<?> type) {
        final CtType<?> overwritten = getOverwrittenClass(type);
        if (overwritten == null) {
            throw new MissingCommonClassException(type);
        }
        return overwritten;
    }

    @Nonnull
    public static List<CtType<?>> getOverwriteTargets() {
        return Objects.requireNonNull(DATA.get().overwriteTargets, OUT_OF_ORDER);
    }

    @Nonnull
    private static List<CtType<?>> getCommonClasses() {
        return Objects.requireNonNull(DATA.get().classes, OUT_OF_ORDER);
    }

    public static Set<File> getMainSourceSet(final Project project) {
        try {
            final JavaPluginConvention javaPlugin = project.getConvention().getPlugin(JavaPluginConvention.class);
            final Set<File> sources = javaPlugin.getSourceSets().getAt("main").getAllJava().getSrcDirs();
            return validateOrEmpty(sources);
        } catch (IllegalStateException ignored) {
            return Collections.emptySet();
        }
    }

    private static Set<File> validateOrEmpty(final Set<File> sources) {
        return sources.stream().filter(File::exists).collect(Collectors.toSet());
    }

    public static void process(final Project project) {
        final Launcher launcher = new Launcher();
        for (final File dir : getMainSourceSet(project)) {
            launcher.addInputResource(new FileSystemFolder(dir));
        }
        final OverwriteValidatorExtension config = OverwriteValidatorExtension.get(project);
        final CtModel model;
        if (config.generateCode()) {
            final Set<CtType<?>> processed = new HashSet<>();
            launcher.setSourceOutputDirectory(config.getOutputDirectory());
            launcher.getEnvironment().setAutoImports(true);
            launcher.addProcessor(new InheritMissingMembersProcessor(processed));
            launcher.addProcessor(new InheritProcessor(processed));
            launcher.addProcessor(new OverwriteClassProcessor(processed));
            launcher.addProcessor(new OverwriteProcessor(processed));
            launcher.setOutputFilter(processed::contains);
            launcher.run();

            ManualImportProcessor.fixImports(project, launcher);
            model = launcher.getModel();
        } else {
            model = launcher.buildModel();
        }
        OverwriteTargetProcessor.processModel(model);
        MissingOverwriteProcessor.processModel(project, model);
    }

    @Nonnull
    private static CtModel createCommonModel(final Project project) {
        final Launcher launcher = new Launcher();
        for (final File dir : getMainSourceSet(project)) {
            launcher.addInputResource(new FileSystemFolder(dir));
        }
        return launcher.buildModel();
    }

    public static class Cache {
        final CtModel model;
        final List<CtType<?>> classes;
        final List<CtType<?>> overwriteTargets;

        Cache(final CtModel model) {
            this.model = model;
            this.classes = CtUtils.getAllClasses(model);
            this.overwriteTargets = OverwriteTargetProcessor.getOverwriteTargets(this.classes);
        }
    }

    private static class MissingCommonClassException extends IllegalStateException {
        MissingCommonClassException(final CtType<?> type) {
            super("Class " + type.getSimpleName() + " has nothing to inherit");
        }
    }
}
