<?php


namespace Goog\Soy;


/**
 * Minimal implementation of the i18n extension API.
 *
 * This is a minimal implementation of the core API for demo purpose.
 */
class SimpleTranslator implements Translator
{

	/**
	 * @inheritdoc
	 */
	public static function isMsgAvailable($msgId)
	{
		return true;
	}



	/**
	 * @inheritdoc
	 */
	public static function prepare($msgId, $msgText, array $msgPlaceholders, $msgDesc = null, $msgMeaning = null)
	{
		return $msgText;
	}



	/**
	 * @inheritdoc
	 */
	public static function render($msg, array $values)
	{
		return self::replace($msg, $values);
	}



	/**
	 * @inheritdoc
	 */
	public static function prepareLiteral($msgId, $msgText, $msgDesc = null, $msgMeaning = null)
	{
		return $msgText;
	}



	/**
	 * @inheritdoc
	 */
	public static function renderLiteral($msg)
	{
		return $msg;
	}



	/**
	 * @inheritdoc
	 */
	public static function preparePlural($msgId, array $msgCases, array $msgPlaceholders, $msgDesc = null, $msgMeaning = null)
	{
		return $msgCases;
	}



	/**
	 * @inheritdoc
	 */
	public static function renderPlural($msg, $caseValue, array $values)
	{
		$key = '=' . $caseValue;
		$msg = isset($msg[$key]) ? $msg[$key] : $msg['other'];
		return self::replace($msg, $values);
	}



	/**
	 * @inheritdoc
	 */
	public static function prepareIcu($msgId, $msgText, array $msgFields, $msgDesc = null, $msgMeaning = null)
	{
		return new \MessageFormatter('en', $msgText);
	}



	/**
	 * @inheritdoc
	 */
	public static function renderIcu($msg, array $values)
	{
		/** @type \MessageFormatter $msg */
		return $msg->format($values);
	}



	/**
	 * @inheritdoc
	 */
	public static function formatNum($value, $targetFormat)
	{
		switch ($targetFormat)
		{
			case 'currency':
				return sprintf('$%.2f', $value);
			case 'percent':
				return sprintf('%.0f%%', $value);
			case 'scientific':
				return sprintf('%.0E', $value);
			case 'decimal':
				return rtrim(rtrim(sprintf('%.3f', $value), '0'), '.');
			case 'compact_short':
				return self::formatCompact($value, true);
			case 'compact_long':
				return self::formatCompact($value, false);
			default:
				return $value;
		}
	}



	/**
	 * Return string with replaced placeholders
	 *
	 * @param string $msg Message
	 * @param array $values Array containing placeholder as keys and their respective values
	 *
	 * @return string
	 */
	private static function replace($msg, array $values)
	{
		return preg_replace_callback(
			'~{(\w+?)}~',
			function ($matches) use ($values) {
				$key = $matches[1];
				return isset($values[$key]) ? $values[$key] : '';
			},
			$msg
		);
	}



	private static $shortSuffixes = [
		1000 => 'K',
		1000000 => 'M',
		1000000000 => 'B',
		1000000000000 => 'T',
	];

	private static $longSuffixes = [
		1000 => ' thousand',
		1000000 => ' million',
		1000000000 => ' billion',
		1000000000000 => ' trillion',
	];

	/**
	 * Compact number formatting using proper suffixes based on magnitude.
	 *
	 * Compact number formatting has slightly idiosyncratic behavior mainly due to
	 * two rules. First, if the value is below 1000, the formatting should just be a
	 * 2 digit decimal formatting. Second, the number is always truncated to leave at
	 * least 2 digits. This means that a number with one digit more than the
	 * magnitude, such as 1250, is still left with 1.2K, whereas one more digit would
	 * leave it without the decimal, such as 12500 becoming 12K.
	 *
	 * @param int|float $value The value to format.
	 * @param bool $short Whether to use the short form suffixes or long form suffixes.
	 *
	 * @return string A formatted number as a string.
	 */
	private static function formatCompact($value, $short = true)
	{
		if ($value < 1000)
		{
			return rtrim(rtrim(sprintf('%.2f', $value), '0'), '.');
		}

		$suffixes = array_reverse($short ? self::$shortSuffixes : self::$longSuffixes);

		foreach ($suffixes as $key => $suffix)
		{
			if ($value >= $key)
			{
				$value = $value / $key;

				if ($value > 10)
				{
					$pattern = '%.0f' . $suffix;
				}
				else
				{
					$pattern = '%$.1f' . $suffix;
				}

				return sprintf($pattern, $value);
			}
		}
	}
}