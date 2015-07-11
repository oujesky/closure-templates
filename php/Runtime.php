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


} 