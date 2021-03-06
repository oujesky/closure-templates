<?php

namespace Goog\Soy;

/**
 * Runtime helper for compiled soy templates.
 *
 * This class provides utility functions required by soy templates compiled with
 * the PHP compilers. These functions handle the runtime internals necessary to
 * match JS behavior in module and function loading, along with type behavior.
 */
class Runtime 
{

	private static $delegateRegistry = [];

	/**
	 * @var array The mapping of css class names for {@link getCssName()}
	 */
	private static $cssNameMapping;


	/**
	 * A helper to implement the Soy Function checkNotNull.
	 *
	 * @param mixed $val The value to test.
	 * @return mixed Value of $val if it was not null.
	 * @throws \RuntimeException If $val is null.
	 */
	public static function checkNotNull($val)
	{
		if ($val === null)
		{
			throw new \RuntimeException('Unexpected null value');
		}

		return $val;
	}



	/**
	 * Safe key based data access.
	 *
	 * @param array $data The data array to search for the key within.
	 * @param string|int $key The key to use for access.
	 *
	 * @return mixed|null $data[$key] if key is present or null otherwise.
	 */
	public static function keySafeDataAccess($data, $key)
	{
		return isset($data[$key]) ? $data[$key] : null;
	}



	/**
	 * Get the delegate function associated with the given template_id/variant.
	 *
	 * Retrieves the (highest-priority) implementation that has been registered for
	 * a given delegate template key (template_id and variant). If no implementation
	 * has been registered for the key, then the fallback is the same template_id
	 * with empty variant. If the fallback is also not registered,
	 * and allow_empty_default is true, then returns an implementation that is
	 * equivalent to an empty template (i.e. rendered output would be empty string).
	 *
	 * @param string $templateId The delegate template id.
	 * @param string|int $variant The delegate template variant (can be an empty string, or a number when a global is used).
	 * @param boolean $allowEmptyDefault Whether to default to the empty template function if there's no active implementation.
	 *
	 * @return callable The retrieved implementation function.
	 *
	 * @throws \RuntimeException When no implementation of one delegate template is found.
	 */
	public static function getDelegateFn($templateId, $variant, $allowEmptyDefault)
	{
		$key = self::genDelegateId($templateId, $variant);
		$fn = isset(self::$delegateRegistry[$key]) ? self::$delegateRegistry[$key][1] : null;

		if (!$fn)
		{
			// fallback to empty variant
			$key = self::genDelegateId($templateId);
			$fn = isset(self::$delegateRegistry[$key]) ? self::$delegateRegistry[$key][1] : null;
		}

		if ($fn)
		{
			return $fn;
		}
		else if ($allowEmptyDefault)
		{
			return function($opt_data = null, $opt_ijData = null) { return ''; };
		}
		else
		{
			throw new \RuntimeException('Found no active impl for delegate call to "'.$templateId.':'.$variant.'" (and not $allowEmptyDefault = true).');
		}

	}



	/**
	 * Register a delegate function in the global registry.
	 *
	 * @param string $templateId The id for the given template.
	 * @param string $variant The variation key for the given template.
	 * @param int $priority The priority value of the given template.
	 * @param callable $fn A unique name of the function generated at compile time.
	 *
	 * @throws \RuntimeException If a delegate was attempted to be added with the same priority an error will be raised.
	 */
	public static function registerDelegateFn($templateId, $variant, $priority, $fn)
	{
		$mapKey = self::genDelegateId($templateId, $variant);

		$currPriority = null;
		$currFn = null;

		if (isset(self::$delegateRegistry[$mapKey]))
		{
			list($currPriority, $currFn) = self::$delegateRegistry[$mapKey];
		}

		if ($currPriority === null || $priority > $currPriority)
		{
			self::$delegateRegistry[$mapKey] = [$priority, $fn];
		}
		else if ($priority == $currPriority && $fn != $currFn)
		{
			throw new \RuntimeException('Encountered two active delegates with the same priority (' . $templateId . ':' . $variant . ':' . $priority . ')');
		}
	}



	private static function genDelegateId($templateId, $variant = '')
	{
		return 'key_' . $templateId . ':' . $variant;
	}



	/**
	 * Return the mapped css class name with modifier.
	 *
	 * Following the pattern of goog.getCssName in closure, this function maps a css
	 * class name to its proper name, and applies an optional modifier.
	 *
	 * If no mapping is present, the $className and $modifier are joined with hyphens
	 * and returned directly.
	 *
	 * If a mapping is present, the resulting css name will be retrieved from the
	 * mapping and returned.
	 *
	 * If one argument is passed it will be processed, if two are passed only the
	 * modifier will be processed, as it is assumed the first argument was generated
	 * as a result of calling goog.getCssName.
	 *
	 * @param string $className The class name to look up.
	 * @param string|null $modifier An optional modifier to append to the $className.
	 *
	 * @return string A mapped class name with optional modifier.
	 */
	public static function getCssName($className, $modifier = null)
	{
		$pieces = [$className];
		if ($modifier)
		{
			$pieces[] = $modifier;
		}

		if (self::$cssNameMapping)
		{
			// Only map the last piece of the name.
			$last = array_pop($pieces);
			$pieces[] = isset(self::$cssNameMapping[$last]) ? self::$cssNameMapping[$last] : $last;
		}

		return implode('-', $pieces);
	}



	/**
	 * A coercion function emulating JS style type conversion in the '+' operator.
	 *
	 * This function is similar to the JavaScript behavior when using the '+'
	 * operator. Variables will will use the default behavior of the '+' operator
	 * until they encounter a type error at which point the more 'simple' type will
	 * be coerced to the more 'complex' type.
	 *
	 * Supported types are null (which is treated like a bool), bool, primitive
	 * numbers (int, float, etc.), and strings. All other objects will be converted
	 * to strings.
	 *
	 * Example:
	 * Runtime::typeSafeAdd(true, true) = 2
	 * Runtime::typeSafeAdd(true, 3) = 4
	 * Runtime::typeSafeAdd(3, 'abc') = '3abc'
	 * Runtime::typeSafeAdd(true, 3, 'abc') = '4abc'
	 * Runtime::typeSafeAdd('abc', true, 3) = 'abctrue3'
	 *
	 * @param array ... List of parameters for addition/coercion.
	 *
	 * @return mixed The result of the addition. The return type will be based on the most
	 * 		'complex' type passed in. Typically an integer or a string.
	 */
	public static function typeSafeAdd()
	{
		$length = func_num_args();

		if ($length === 0)
		{
			return null;
		}

		$args = func_get_args();

		// JS operators can sometimes work as unary operators. So, we fall back to the
		// initial value here in those cases to prevent ambiguous output.
		if ($length === 1)
		{
			return $args[0];
		}

		$isString = is_string($args[0]);
		$result = $args[0];
		for ($i = 1; $i < $length; $i++)
		{
			$arg = $args[$i];
			if ($isString)
			{
				$arg = self::convertToJsString($arg);
				$result .= $arg;
			}
			// Special case for null which can be converted to bool but is not
			// autocoerced. This can result in a conversion of result from a boolean to
			// a number (which can affect later string conversion) and should be
			// retained.
			else if ($args === null)
			{
				$result += false;
			}
			else if (is_string($arg))
			{
				$result = self::convertToJsString($result) . self::convertToJsString($arg);
				$isString = true;
			}
			else
			{
				$result += $arg;
			}
		}

		return $result;
	}



	/**
	 * Convert a value to a string, with the JS string values for primitives.
	 *
	 * @param mixed $value The value to stringify.
	 *
	 * @return string A string representation of value. For primitives, ensure that the result
	 * 		matches the string value of their JS counterparts.
	 */
	private static function convertToJsString($value)
	{
		if ($value === null)
		{
			return 'null';
		}
		else if ($value === true)
		{
			return 'true';
		}
		else if ($value === false)
		{
			return 'false';
		}
		else
		{
			return strval($value);
		}
	}


}