<?php

namespace Goog\Soy;

/**
 * Escaping functions for compiled soy templates.
 *
 * This class contains the public functions to sanitize content for different
 * contexts.
 *
 * The bulk of the logic resides in GeneratedSanitize.php which is generated by
 * GeneratePhpSanitizeEscapingDirectiveCode.java to match other implementations.
 * Please keep as much escaping and filtering logic/regex in there as possible.
 */
class Sanitize
{

	/**
	 * Matches html attribute endings which are ambiguous (not ending with space or quotes).
	 */
	const _AMBIGUOUS_ATTR_END_RE = '~([^"\'\s])$~';

	/**
	 * Matches any/only HTML5 void elements' start tags.
	 * See http://www.w3.org/TR/html-markup/syntax.html#syntax-elements
	 */
	const _HTML5_VOID_ELEMENTS_RE = '~^<(?:area|base|br|col|command|embed|hr|img|input|keygen|link|meta|param|source|track|wbr)\\b~';


	/**
	 * An innocuous output to replace filtered content with.
	 * # For details on its usage, see the description in
	 */
	const _INNOCUOUS_OUTPUT = 'zSoyz';


	/**
	 * Regex for various newline combinations.
	 */
	const _NEWLINE_RE = '~(\r\n|\r|\n)~';


	/**
	 * Regex for finding replacement tags.
	 */
	const _REPLACEMENT_TAG_RE = '~\[(\d+)\]~';



	public static function changeNewlineToBr($value)
	{
		$result = preg_replace(self::_NEWLINE_RE, '<br>', (string)$value);

		if (self::isContentKind($value, ContentKind::HTML))
		{
			return new SanitizedHtml($result, self::getContentDir($value));
		}

		return $value;
	}

	public static function cleanHtml($value, $safeTags = null)
	{
		if (!$safeTags)
		{
			$safeTags = GeneratedSanitize::$_SAFE_TAG_WHITELIST;
		}

		if (self::isContentKind($value, ContentKind::HTML))
		{
			return $value;
		}

		return new SanitizedHtml(
			self::stripHtmlTags($value, $safeTags),
			self::getContentDir($value)
		);
	}

	public static function escapeCssString($value)
	{
		return GeneratedSanitize::escapeCssStringHelper($value);
	}

	public static function escapeHtml($value)
	{
		if (self::isContentKind($value, ContentKind::HTML))
		{
			return $value;
		}

		return new SanitizedHtml(
			GeneratedSanitize::escapeHtmlHelper($value),
			self::getContentDir($value)
		);
	}

	public static function escapeHtmlAttribute($value)
	{
		if (self::isContentKind($value, ContentKind::HTML))
		{
			/** @type SanitizedHtml $value */
			return GeneratedSanitize::normalizeHtmlHelper(
				self::stripHtmlTags($value->getContent())
			);
		}

		return GeneratedSanitize::escapeHtmlHelper($value);
	}

	public static function escapeHtmlAttributeNospace($value)
	{
		if (self::isContentKind($value, ContentKind::HTML))
		{
			/** @type SanitizedHtml $value */
			return GeneratedSanitize::normalizeHtmlNospaceHelper(
				self::stripHtmlTags($value->getContent())
			);
		}

		return GeneratedSanitize::escapeHtmlNospaceHelper($value);
	}

	public static function escapeHtmlRcdata($value)
	{
		if (self::isContentKind($value, ContentKind::HTML))
		{
			/** @type SanitizedHtml $value */
			return GeneratedSanitize::normalizeHtmlHelper($value->getContent());
		}

		return GeneratedSanitize::escapeHtmlHelper($value);
	}

	public static function escapeJsRegex($value)
	{
		return GeneratedSanitize::escapeJsRegexHelper($value);
	}

	public static function escapeJsString($value)
	{
		if (self::isContentKind($value, ContentKind::JS_STR_CHARS))
		{
			/** @type SanitizedJsStrChars $value */
			return $value->getContent();
		}

		return GeneratedSanitize::escapeJsStringHelper($value);
	}

	public static function escapeJsValue($value)
	{
		if ($value === null)
		{
			// We output null for compatibility with Java, as it returns null from maps
			// where there is no corresponding key.
			return ' null ';
		}

		if (self::isContentKind($value, ContentKind::JS))
		{
			/** @type SanitizedJs $value */
			return $value->getContent();
		}

		// We surround values with spaces so that they can't be interpolated into
		// identifiers by accident.
		// We could use parentheses but those might be interpreted as a function call.
		// This matches the JS implementation in javascript/template/soy/soyutils.js.
		if (is_numeric($value))
		{
			return ' ' . $value . ' ';
		}

		return '\'' . GeneratedSanitize::escapeJsStringHelper($value) . '\'';
	}

	public static function escapeUri($value)
	{
		return GeneratedSanitize::escapeUriHelper($value);
	}

	public static function filterCssValue($value)
	{
		if (self::isContentKind($value, ContentKind::CSS))
		{
			/** @type SanitizedCss $value */
			return $value->getContent();
		}

		if ($value === null)
		{
			return '';
		}

		return GeneratedSanitize::filterCssValueHelper($value);
	}

	public static function filterHtmlAttributes($value)
	{
		// NOTE: Explicitly no support for SanitizedContentKind.HTML, since that is
		// meaningless in this context, which is generally *between* html attributes.
		if (self::isContentKind($value, ContentKind::ATTRIBUTES))
		{
			/** @type SanitizedHtmlAttribute $value */
			// Add a space at the end to ensure this won't get merged into following
			// attributes, unless the interpretation is unambiguous (ending with quotes
			// or a space).
			return preg_replace(self::_AMBIGUOUS_ATTR_END_RE, '\\1', $value->getContent());
		}

  		// TODO(gboyer): Replace this with a runtime exception along with other
  		return GeneratedSanitize::filterHtmlAttributesHelper($value);
	}

	public static function filterHtmlElementName($value)
	{
		// NOTE: We don't accept any SanitizedContent here. HTML indicates valid
		// PCDATA, not tag names. A sloppy developer shouldn't be able to cause an
		// exploit:
		// ... {let userInput}script src=http://evil.com/evil.js{/let} ...
		// ... {param tagName kind="html"}{$userInput}{/param} ...
		// ... <{$tagName}>Hello World</{$tagName}>
		return GeneratedSanitize::filterHtmlElementNameHelper($value);
	}

	public static function filterImageDataUri($value)
	{
		return new SanitizedUri(GeneratedSanitize::filterImageDataUriHelper($value));
	}

	public static function filterNoAutoEscape($value)
	{
		if (self::isContentKind($value, ContentKind::TEXT))
		{
			return self::_INNOCUOUS_OUTPUT;
		}

		return $value;
	}

	public static function filterNormalizeUri($value)
	{
		if (self::isContentKind($value, ContentKind::URI))
		{
			return self::normalizeUri($value);
		}

		return GeneratedSanitize::filterNormalizeUriHelper($value);
	}

	public static function normalizeHtml($value)
	{
		return GeneratedSanitize::normalizeHtmlHelper($value);
	}

	public static function normalizeUri($value)
	{
		return GeneratedSanitize::normalizeUriHelper($value);
	}

	public static function getContentDir($value)
	{
		return $value instanceof SanitizedContent ? $value->getContentDir() : null;
	}

	public static function isContentKind($value, $contentKind)
	{
		return $value instanceof SanitizedContent && $value->getContentKind() == $contentKind;
	}



	/**
	 * Strip any html tags not present on the whitelist.
	 *
	 * If there's a whitelist present, the handler will use a marker for whitelisted
	 * tags, strips all others, and then reinserts the originals.
	 *
	 * @param string $value The input string.
	 * @param array|null $tagWhitelist An array of safe tag names.
	 *
	 * @return string
	 */
	private static function stripHtmlTags($value, $tagWhitelist = null)
	{
		if (!$tagWhitelist)
		{
			// The second level (replacing '<' with '&lt;') ensures that non-tag uses of
			// '<' do not recombine into tags as in
			// '<<foo>script>alert(1337)</<foo>script>'
			return preg_replace(GeneratedSanitize::_LT_REGEX, '&lt;',
				preg_replace(GeneratedSanitize::_HTML_TAG_REGEX, '', $value));
		}

		// Escapes '[' so that we can use [123] below to mark places where tags
		// have been removed.
		$html = str_replace('[', '&#91;', (string)$value);

		// Consider all uses of '<' and replace whitelisted tags with markers like
		// [1] which are indices into a list of approved tag names.
		// Replace all other uses of < and > with entities.
		$tags = [];
		$html = preg_replace_callback(
			GeneratedSanitize::_HTML_TAG_REGEX,
			function($match) use ($tagWhitelist, &$tags) {
				return self::tagSubHandler($tagWhitelist, $tags, $match);
			},
			$html
		);

		// Escape HTML special characters. Now there are no '<' in html that could
		// start a tag.
		$html = GeneratedSanitize::normalizeHtmlHelper($html);

		// Discard any dead close tags and close any hanging open tags before
		// reinserting white listed tags.
		$finalCloseTags = self::balanceTags($tags);

		// Now html contains no tags or less-than characters that could become
		// part of a tag via a replacement operation and tags only contains
		// approved tags.
		// Reinsert the white-listed tags.
		$html = preg_replace_callback(
			self::_REPLACEMENT_TAG_RE,
			function($match) use ($tags) {
				return $tags[(int)$match[1]];
			},
			$html
		);

		// Close any still open tags.
		// This prevents unclosed formatting elements like <ol> and <table> from
		// breaking the layout of containing HTML.
		return $html . $finalCloseTags;
	}



	/**
	 * Replace whitelisted tags with markers and update the tag list.
	 *
	 * @param array $tagWhitelist A list containing all whitelisted html tags.
	 * @param array $tags The list of all whitelisted tags found in the text.
	 * @param array $match The current match element with a subgroup containing the tag name.
	 *
	 * @return string The replacement content, a index marker for whitelisted tags, or an empty string.
	 */
	private static function tagSubHandler(array $tagWhitelist, array &$tags, array $match)
	{
		$tag = $match[0];
		$name = $match[1];
		$name = mb_strtolower($name);
		if (in_array($name, $tagWhitelist))
		{
			$start = $tag[1] == '/' ? '</' : '<';
			$index = count($tags);
			$tags[] = $start . $name . '>';
			return '[' . $index . ']';
		}

		return '';
	}



	/**
	 * Throw out any close tags without an open tag.
	 *
	 * If {@code <table>} is used for formatting, embedded HTML shouldn't be able
	 * to use a mismatched {@code </table>} to break page layout.
	 *
	 * @param array $tags The list of all tags in this text.
	 *
	 * @return string A string containing zero or more closed tags that close all elements that
	 * are opened in tags but not closed.
	 */
	private static function balanceTags(array $tags)
	{
		$openTags = [];
		foreach ($tags as $i => $tag)
		{
			if ($tag[1] == '/')
			{
				$index = count($openTags) - 1;
				while ($index >= 0 && $openTags[$index] != $tag)
				{
					$index--;
				}

				if ($index < 0)
				{
					$tags[$i] = ''; // Drop close tag
				}
				else
				{
					$tags[$i] = implode('', array_reverse(array_slice($openTags, $index)));
					$openTags = array_slice($openTags, 0, $index - 1);
				}
			}
			else if (!preg_match(self::_HTML5_VOID_ELEMENTS_RE, $tag))
			{
				$openTags[] = '</' . mb_substr($tag, 1);
			}
		}

		return implode('', array_reverse($openTags));
	}

}