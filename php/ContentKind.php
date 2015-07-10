<?php

namespace Goog\Soy;

abstract class ContentKind
{
	const HTML = 1;
	const JS = 2;
	const JS_STR_CHARS = 3;
	const URI = 4;
	const ATTRIBUTES = 5;
	const CSS = 6;
	const TEXT = 7;

	public static function decodeKind($i)
	{
		switch ($i)
		{
			case self::HTML:
				return 'HTML';
			case self::JS:
				return 'JS';
			case self::JS_STR_CHARS:
				return 'JS_STR_CHARS';
			case self::URI:
				return 'URI';
			case self::ATTRIBUTES:
				return 'ATTRIBUTES';
			case self::CSS:
				return 'CSS';
			case self::TEXT:
				return 'TEXT';
			default:
				return null;
		}
	}
} 