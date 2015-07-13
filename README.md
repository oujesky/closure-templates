# Soy to PHP compiler

**Warning**: This is unofficial experimental fork of Google [Closure Templates](https://github.com/google/closure-templates), use at your own discretion.

This fork is capable of compiling Soy templates to PHP. The compiler is mostly based on official work-in-progress compiler for Python. As such, the PHP compiler always uses strict autoescaping.

## Soy to PHP compilation

You can download latest [SoyToPhpCompiler.jar](https://github.com/oujesky/closure-templates/releases/download/php-jar/SoyToPhpSrcCompiler.jar) or build it yourself with standard `mvn package` command

To run the compiler, use following command:
```
java -jar SoyToPhpSrcCompiler.jar --srcs file.soy --outputPathFormat {INPUT_FILE_NAME}.php
```

PHP specific optional parameters:
* `--translationClass` (default `Goog\Soy\SimpleTranslator`) - Full name of PHP class responsible for translation of `{msg}` tags.

## PHP usage

To use your compiled PHP templates, you need to include classes in `php` subdirectory to your project together with the compiled PHP Soy templates. 

The template itself is static method of a generated class. To get the result, you simply call it with optional parameter array. The result is instance of `Goog\Soy\SanitizedContent` subclass, which can be typecasted to `string` and echoed.

Example:
```
{namespace my.soy.CompiledTemplate}

{template .hello}
    {@param name: string}
    <p>Hello <string>{$name}</strong>!</p>
{/template}
```

```php
use My\Soy\CompiledTemplate;

echo CompiledTemplate::hello([
    'name' => 'World'
]);
```

## Translations
For `{msg}` tags, generated PHP code contains calls to static methods defined by `Goog\Soy\Translator` interface. The basic default implementation is handled by `Goog\Soy\SimpleTranslator` class which simply uses the default text as is defined in the Soy template. 

You can supply your own implementation of the interface with `--translationClass` command line option.

## TODO
* Complete suite of unit tests for PHP compilation part
* Full BiDi support
* Composer repository for runtime PHP classes

---

# Closure Templates
Closure Templates are a client- and server-side templating system that helps you
dynamically build reusable HTML and UI elements. They have a simple syntax
that is natural for programmers, and you can customize them to fit your
application's needs.  In contrast to traditional templating systems,in which
you must create one monolithic template per page, you can think of
Closure Templates as small components that you compose to form your user
interface. You can also use the built-in message support to easily localize
your applications.

Closure Templates are implemented for both JavaScript and Java, so that you can
use the same templates on both the server and client side. They use a data model
and expression syntax that work for either language. For the client side,
Closure Templates are precompiled into efficient JavaScript.

## What are the benefits of using Closure Templates?
* **Convenience**. Closure Templates provide an easy way to build pieces of HTML
  for your application's UI and help you separate application logic from
   display.
* **Language-neutral**. Closure Templates work with JavaScript or Java. You can
  write one template and share it between your client- and server-side code.
* **Client-side speed**. Closure Templates are compiled to efficient JavaScript
  functions for maximum client-side performance.
* **Easy to read**. You can clearly see the structure of the output HTML from
  the structure of the template code. Messages for translation are inline for
  extra readability.
* **Designed for programmers**. Templates are simply functions that can call
  each other. The syntax includes constructs familiar to programmers.
  You can put multiple templates in one source file.
* **A tool, not a framework**. Works well in any web application environment
  in conjunction with any libraries, frameworks, or other tools.
* **Battle-tested**. Closure Templates are used extensively in some of the largest
  web applications in the world, including Gmail and Google Docs.
* **Secure**. Closure Templates are contextually autoescaped to reduce the risk
  of XSS.

## Getting Started
* Work through [Hello World Using JavaScript](https://developers.google.com/closure/templates/docs/helloworld_js).
* Work through [Hello World Using Java](https://developers.google.com/closure/templates/docs/helloworld_java).
* Read the [Documentation](https://developers.google.com/closure/templates/docs/overview).