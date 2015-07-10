<?php

namespace Goog\Soy;

/**
 * Functionality for basic print directives.
 *
 * This class contains functions to support all non-escaping related print
 * directives which do not have direct PHP support. Escaping directives live in
 * Sanitize.php.
 */
class Directives
{

	/**
	 * Truncate a string to the max_len and optionally add ellipsis.
	 *
	 * @param string $value The input string to truncate.
	 * @param int $maxLen The maximum length allowed for the result.
	 * @param boolean $addEllipsis Whether or not to add ellipsis.
	 *
	 * @return string A truncated string.
	 */
	public static function truncate($value, $maxLen, $addEllipsis)
	{
		if (mb_strlen($value) <= $maxLen)
		{
			return $value;
		}

		// If the max_len is too small, ignore any ellipsis logic.
		if (!$addEllipsis || $maxLen <= 3)
		{
			return mb_substr($value, 0, $maxLen);
		}

		// Reduce max_len to compensate for the ellipsis length.
		$maxLen -= 3;

		// Truncate and add the ellipsis.
		return mb_substr($value, 0, $maxLen) . '...';
	}

} 