<?php

namespace Goog\Soy;

/**
 * This interface defines the API for a valid i18n extension.
 *
 * All valid i18n extensions should implement methods defined in this class.
 */
interface Translator
{

	/**
	 * Determines whether an individual message is available for translation.
	 *
	 * @param string $msgId The id of the message to test.
	 *
	 * @return bool Whether the message is available for translation.
	 */
	public static function isMsgAvailable($msgId);



	/**
	 * Prepares an I18N string for later rendering.
	 *
	 * This function takes a message identifier, the message text, and a
	 * list of placeholder names, and wraps them in some opaque
	 * data structure that's easy to render for the underlying system.
	 *
	 * The result is passed to the {@Link render()} function, along with the
	 * variables to insert.
	 *
	 * This method is typically called once for each source message, at
	 * module import time.  It can be called at other times, though, and
	 * may in theory be called multiple times for the same message, so
	 * shouldn't have any side effects.
	 *
	 * A given source string is keyed on the message identifier.
	 *
	 * The placeholders list is a list of identifiers.
	 *
	 * For embedded expressions, this is the name of a temporary variable
	 * in Soy format. For HTML expressions, the placeholders are the
	 * names of Soy-generated HTML tag names.
	 *
	 * The text string uses Soy syntax to mark where the placeholders
	 * should be inserted, e.g.
	 *
	 * // embedded expression
	 * $msgText = 'This is a {$value}';
	 * $msgPlaceholders = ['$value'];
	 *
	 * // soy generated HTML tags
	 * $msgText = 'Please click {START_LINK}here{END_LINK}.';
	 * $msgPlaceholders = ['START_LINK', 'END_LINK'];
	 *
	 * If the string has no placeholders, consider using {@link prepareLiteral()}.
	 *
	 * @param string $msgId Message identifier. This is the same identifier as you
	 * 		get when extracting messages to the TC, given as a short numeric string.
	 * @param string $msgText Message text. Placeholders are marked as {name}. To add { or } to the
	 * 		string itself, write as {{ or }}.
	 * @param array $msgPlaceholders Array containing placeholder names. If there are no placeholders
	 * 		in the string, consider using the literal API instead.
	 * @param null|string $msgDesc Message description.
	 * @param null|string $msgMeaning Message meaning.
	 *
	 * @return mixed Value that's passed to the {@link render()} method to render this string.
	 */
	public static function prepare($msgId, $msgText, array $msgPlaceholders, $msgDesc = null, $msgMeaning = null);



	/**
	 * Renders a prepared I18N string.
	 *
	 * This takes the value returned by {@link prepare()} and
	 * renders it to a string (or any other object that can be placed in
	 * the output buffer).
	 *
	 * @param mixed $msg A message value created by {@link prepare()}.
	 * @param array $values
	 *
	 * @return string
	 */
	public static function render($msg, array $values);



	/**
	 * Prepares an I18N literal string for later rendering.
	 *
	 * Same as {@link prepare()}, but doesn't support placeholders.
	 *
	 * @param string $msgId Message identifier.
	 * @param string $msgText Message text.
	 * @param null|string $msgDesc Message description.
	 * @param null|string $msgMeaning Message meaning.
	 *
	 * @return mixed Value that's passed to the {@link renderLiteral()} method to render this string.
	 */
	public static function prepareLiteral($msgId, $msgText, $msgDesc = null, $msgMeaning = null);



	/**
	 * Renders a prepared I18N literal string.
	 *
	 * @param mixed $msg A message value created by {@link prepareLiteral()}.
	 *
	 * @return string The rendered string.
	 */
	public static function renderLiteral($msg);



	/**
	 * Prepare an ICU string for rendering.
	 *
	 * Same as prepare(), but takes msg_cases dict instead of raw msg_text.
	 * This is used for plural translation. (To be more precise, plural msg node
	 * without genders attribute, the later is rewritten by the compiler to
	 * select node)
	 *
	 * @param string $msgId Message identifier. This is the same identifier as you
	 *      get when extracting messages to the TC, given as a short numeric string.
	 * @param array $msgCases An array map from case spec string to a branch msgText.
	 * 		Case spec comes in two possible format. "=<number>" for explicit value,
	 * 		or the string "other" for default. The values of the array are in the
	 * 		same format of msgText in the general {@link prepare()} methods.
	 * 		For example: ['other' => '{$numDrafts} drafts', '=0' => 'No drafts', '=1' => '1 draft']
	 * @param array $msgPlaceholders Array containing placeholder names. If there are no placeholders
	 * 		in the string, consider using the literal API instead.
	 * @param null|string $msgDesc Message description.
	 * @param null|string $msgMeaning Message meaning.
	 *
	 * @return mixed Value that's passed to the {@link renderPlural()} method to render this string.
	 */
	public static function preparePlural($msgId, array $msgCases, array $msgPlaceholders, $msgDesc = null, $msgMeaning = null);



	/**
	 * Renders a prepared plural msg object.
	 *
	 * @param mixed $msg A message value created by {@link preparePlural()}.
	 * @param int $caseValue An integer for the case value.
	 * @param array $values An array contains names and values for the corresponding placeholders.
	 *
	 * @return string The rendered string.
	 */
	public static function renderPlural($msg, $caseValue, array $values);



	/**
	 * Prepare an ICU string for rendering.
	 *
	 * Same as {@link prepare()}, but takes a string in ICU syntax for
	 * msgText.  This is used for select and plural translation.
	 *
	 * @param string $msgId Message identifier.
	 * @param string $msgText An ICU string.
	 * @param array $msgFields An array containing the names of all configurable ICU fields.
	 * @param null|string $msgDesc Message description.
	 * @param null|string $msgMeaning Message meaning.
	 *
	 * @return mixed Value that's passed to the {@link renderIcu()} method to render this string.
	 */
	public static function prepareIcu($msgId, $msgText, array $msgFields, $msgDesc = null, $msgMeaning = null);



	/**
	 * Renders a prepared ICU string object.
	 *
	 * @param mixed $msg A message value created by {@link prepareIcu()}.
	 * @param array $values An array contains names and values for the corresponding placeholders.
	 *
	 * @return string
	 */
	public static function renderIcu($msg, array $values);



	/**
	 * Formats a number into a specific format (decimal, currency, etc.).
	 *
	 * @param int|double $value The value to format.
	 * @param string $targetFormat The target number format.
	 *
	 * @return string The given number formatted into a string.
	 */
	public static function formatNum($value, $targetFormat);
} 