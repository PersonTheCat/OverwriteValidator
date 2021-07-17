package personthecat.overwritevalidator.processors;

import personthecat.overwritevalidator.CtUtils;
import personthecat.overwritevalidator.LauncherContext;
import personthecat.overwritevalidator.annotations.Overwrite;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.Set;
import java.util.Objects;

public class OverwriteProcessor extends AbstractProcessor<CtType<?>> {

    private final Set<CtType<?>> processed;

    public OverwriteProcessor(final Set<CtType<?>> processed) {
        this.processed = processed;
    }

    @Override
    public void process(final CtType<?> type) {
        if (CtUtils.anyMemberIsAnnotated(type, Overwrite.class)) {
            this.processMembers(type, LauncherContext.getOverwrittenClassOrThrow(type));
            this.processed.add(type);
        }
    }

    private void processMembers(final CtType<?> type, final CtType<?> overwritten) {
        this.processMethods(type, overwritten);
        this.processFields(type, overwritten);
        this.processConstructors(type, overwritten);
        this.processRecursive(type, overwritten);
    }

    private void processMethods(final CtType<?> type, final CtType<?> overwritten) {
        for (final CtMethod<?> method : type.getMethods()) {
            final CtAnnotation<?> a = CtUtils.getAnnotation(type, method, Overwrite.class);
            if (a != null) {
                final CtMethod<?> inherited = CtUtils.getOverriddenMethod(overwritten, method);
                Objects.requireNonNull(inherited, "No overwrite target for method: " + method.getSimpleName());
                method.removeAnnotation(a);
            }
        }
    }

    private void processFields(final CtType<?> type, final CtType<?> overwritten) {
        for (final CtField<?> field : type.getFields()) {
            final CtAnnotation<?> a = CtUtils.getAnnotation(type, field, Overwrite.class);
            if (a != null) {
                final CtField<?> inherited = overwritten.getField(field.getSimpleName());
                Objects.requireNonNull(inherited, "No overwrite target for field: " + field.getSimpleName());
                field.removeAnnotation(a);
            }
        }
    }

    private void processConstructors(final CtType<?> type, final CtType<?> overwritten) {
        for (final CtConstructor<?> constructor : CtUtils.getConstructors(type)) {
            final CtAnnotation<?> a = CtUtils.getAnnotation(type, constructor, Overwrite.class);
            if (a != null) {
                final CtConstructor<?> inherited = CtUtils.getOverriddenConstructor(overwritten, constructor);
                Objects.requireNonNull(inherited, "No overwritten target for constructor: " + type.getSimpleName());
                constructor.removeAnnotation(a);
            }
        }
    }

    private void processRecursive(final CtType<?> type, final CtType<?> overwritten) {
        for (final CtType<?> nested : type.getNestedTypes()) {
            final CtType<?> replaced = overwritten.getNestedType(nested.getSimpleName());
            if (replaced != null) {
                this.processMembers(nested, replaced);
            }
        }
    }
}
