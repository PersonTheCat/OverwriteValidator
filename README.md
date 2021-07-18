# OverwriteValidator
[Overwrite Validator](https://github.com/PersonTheCat/OverwriteValidator/blob/main/src/main/java/personthecat/overwritevalidator/OverwriteValidator.java) 
is a build tool designed for Java projects targeting multiple platforms. It allows you to create multiple 
source sets which inherit from a common parent. Any class file which replaces another class in the common 
source set is considered an `@OverwriteClass` and will be implicitly validated by the plugin to ensure that 
it contains the same public members.

In addition, this plugin is capable of generating code to add and replace class members at compile time such
that an overwrite class may be treated as an extension of its parent. 

## Setup

To get started, add the following code to your platform build script:
```gradle
import personthecat.overwritevalidator.OverwriteValidator

buildscript {
    repositories {
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath group: 'com.github.PersonTheCat', name: 'OverwriteValidator', version: '1.3'
    }
}

apply plugin: OverwriteValidator
```

In order to support code generation via [OverwriteValidatorAnnotations](https://github.com/PersonTheCat/OverwriteValidatorAnnotations),
you should declare this dependency in your script:

```gradle
repositories [
  maven { url 'https://jitpack.io' }
}
dependencies {
  compileOnly group: 'com.github.PersonTheCat', name: 'OverwriteValidatorAnnotations', version: '1.2'
}
```

If you _do not_ wish to enable code generation, you should **explicitly disable it** via the following
[extension](https://github.com/PersonTheCat/OverwriteValidator/blob/main/src/main/java/personthecat/overwritevalidator/OverwriteValidatorExtension.java):

```gradle
overwriteValidator {
  validateOnly()
}
```

This plugin also supports a couple of settings used for configuring the output directory and location
of the common project. Here they are configured to their default values:

```gradle
overwriteValidator {
  outputDirectory file("$buildDir/generated/sources/validator")
  commonProject project(':common')
}
```

Note that, as of this time, **OverwriteValidator does not support dependency management** for your
platform code. You will need to manually set up dependencies on your common code and resolve duplicate
class file issues for the time being.

## Implementation

Using this project without annotation support requires no further setup. **It is unnecessary to annotate
your classes** if you only want to validate their contents.

### Using Annotations for Validation

To get started _with_ annotations, let's define a simple target class in our common code:

```java
import personthecat.overwritevalidator.annotations.OverwriteTarget;

@OverwriteTarget
public class Demo {
  // Constants are inlined and thus cannot be overwritten.
  public static final Supplier<String> FIELD = () -> "Hello, World!";
  
  // Members do not have to be static.
  public String method() {
    return "Hi, Mom!";
  }
}
```

By default, the `@OverwriteTarget` annotation is intended for documentation purposes. It should indicate
to the reader that there may be an overwrite class in the platform code. To require that all platforms
overwrite this class, set `reqired` to `true`.

```java
@OverwriteTarget(required = true)
```

### Using Annotations to Validate Platform Code

Now let's look at the platform code which overwrites this class.

```java
import personthecat.overwritevalidator.annotations.Overwrite;
import personthecat.overwritevalidator.annotations.OverwriteClass;

@OverwriteClass
public class Demo {

  @Overwrite
  public static final Supplier<String> FIELD = () -> "foo";
  
  @Overwrite
  public String method() {
    return "bar";
  }
  
  public static boolean instanceOnly() {
    return true;
  }
}
```

### Notes About Platform Annotations

Notice that overwrite classes are allowed to declare unique members which are only available on the given
platform.

As with `@Override`, the `@Overwrite` annotation is validated by the compiler to ensire that an equivalent
method or field exists in common code.

### Using Annotations to Generate Code

If a given member does not require an overwrite for the current platform, its body can be **inherited**.

```java
@OverwriteClass
@InheritMissingMembers
public class Demo {
  public static boolean instanceOnly() {
    return true;
  }
}
```

Be careful using this feature, as your IDE will not be aware. Your code may compile and function correctly,
but **you will still see errors in your editor**.

For this reason, Overwrite Validator provides the `@Inherit` annotation so that members may be explicitly
defined without needing to duplicate any code.

```java
@OverwriteClass
public class Demo {

  @Inherit
  public static final Supplier<String> FIELD = () -> {
    throw new AssertionError();
  };
  
  @Inherit
  public String method() {
    throw new AssertionError();
  }
}
```

### Required Overwrites and Inheritance

Finally, Overwrite Validator provides a couple of annotations used to require that platform code either 
inherit or overwrite specific members.

```java
@OverwriteTarget
public class Demo {

  @PlatformMustOverwrite
  public static final Supplier<String> FIELD = () -> {
    throw new AssertionError();
  };
  
  @PlatformMustInherit
  public String method() {
    return "bar";
  }
}
```

### This Project is Powered by [Spoon!](https://spoon.gforge.inria.fr/)

And this may have been a mistake. Spoon does not have the context it needs to understand where most
references are coming from. One of the consequences of this problem is that generated code very
frequently winds up with broken references and imports. 

I have done my best to fix this, thanks to this project's very restrictive use case, which gives me
enough information to reconstruct the broken code. But, this is not a very solid foundation. If you
have suggestions or can help me improve this project, **please** create an issue here on GitHub.

Thanks!
