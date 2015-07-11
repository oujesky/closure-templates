<?php
// START GENERATED CODE FOR ESCAPERS.

namespace Goog\Soy;

class GeneratedSanitize 
{

private static $_ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE = [
  "\x00" => '&#0;',
  "\x09" => '&#9;',
  "\x0a" => '&#10;',
  "\x0b" => '&#11;',
  "\x0c" => '&#12;',
  "\x0d" => '&#13;',
  ' ' => '&#32;',
  '"' => '&quot;',
  '&' => '&amp;',
  '\'' => '&#39;',
  '-' => '&#45;',
  '/' => '&#47;',
  '<' => '&lt;',
  '=' => '&#61;',
  '>' => '&gt;',
  '`' => '&#96;',
  "\x85" => '&#133;',
  "\xa0" => '&#160;',
  "\xe2\x80\xa8" => '&#8232;',
  "\xe2\x80\xa9" => '&#8233;'
];

private static function _REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE($match) {
  $ch = $match[0];
  $map = self::$_ESCAPE_MAP_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE;
  return isset($map[$ch]) ? $map[$ch] : '';
}
private static $_ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX = [
  "\x00" => '\\x00',
  "\x08" => '\\x08',
  "\x09" => '\\t',
  "\x0a" => '\\n',
  "\x0b" => '\\x0b',
  "\x0c" => '\\f',
  "\x0d" => '\\r',
  '"' => '\\x22',
  '$' => '\\x24',
  '&' => '\\x26',
  '\'' => '\\x27',
  '(' => '\\x28',
  ')' => '\\x29',
  '*' => '\\x2a',
  '+' => '\\x2b',
  ',' => '\\x2c',
  '-' => '\\x2d',
  '.' => '\\x2e',
  '/' => '\\/',
  ':' => '\\x3a',
  '<' => '\\x3c',
  '=' => '\\x3d',
  '>' => '\\x3e',
  '?' => '\\x3f',
  '[' => '\\x5b',
  '\\' => '\\\\',
  ']' => '\\x5d',
  '^' => '\\x5e',
  '{' => '\\x7b',
  '|' => '\\x7c',
  '}' => '\\x7d',
  "\x85" => '\\x85',
  "\xe2\x80\xa8" => '\\u2028',
  "\xe2\x80\xa9" => '\\u2029'
];

private static function _REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX($match) {
  $ch = $match[0];
  $map = self::$_ESCAPE_MAP_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX;
  return isset($map[$ch]) ? $map[$ch] : '';
}
private static $_ESCAPE_MAP_FOR_ESCAPE_CSS_STRING = [
  "\x00" => '\\0 ',
  "\x08" => '\\8 ',
  "\x09" => '\\9 ',
  "\x0a" => '\\a ',
  "\x0b" => '\\b ',
  "\x0c" => '\\c ',
  "\x0d" => '\\d ',
  '"' => '\\22 ',
  '&' => '\\26 ',
  '\'' => '\\27 ',
  '(' => '\\28 ',
  ')' => '\\29 ',
  '*' => '\\2a ',
  '/' => '\\2f ',
  ':' => '\\3a ',
  ';' => '\\3b ',
  '<' => '\\3c ',
  '=' => '\\3d ',
  '>' => '\\3e ',
  '@' => '\\40 ',
  '\\' => '\\5c ',
  '{' => '\\7b ',
  '}' => '\\7d ',
  "\x85" => '\\85 ',
  "\xa0" => '\\a0 ',
  "\xe2\x80\xa8" => '\\2028 ',
  "\xe2\x80\xa9" => '\\2029 '
];

private static function _REPLACER_FOR_ESCAPE_CSS_STRING($match) {
  $ch = $match[0];
  $map = self::$_ESCAPE_MAP_FOR_ESCAPE_CSS_STRING;
  return isset($map[$ch]) ? $map[$ch] : '';
}
private static $_ESCAPE_MAP_FOR_ESCAPE_URI__AND__NORMALIZE_URI__AND__FILTER_NORMALIZE_URI = [
  "\x00" => '%00',
  "\x01" => '%01',
  "\x02" => '%02',
  "\x03" => '%03',
  "\x04" => '%04',
  "\x05" => '%05',
  "\x06" => '%06',
  "\x07" => '%07',
  "\x08" => '%08',
  "\x09" => '%09',
  "\x0a" => '%0A',
  "\x0b" => '%0B',
  "\x0c" => '%0C',
  "\x0d" => '%0D',
  "\x0e" => '%0E',
  "\x0f" => '%0F',
  "\x10" => '%10',
  "\x11" => '%11',
  "\x12" => '%12',
  "\x13" => '%13',
  "\x14" => '%14',
  "\x15" => '%15',
  "\x16" => '%16',
  "\x17" => '%17',
  "\x18" => '%18',
  "\x19" => '%19',
  "\x1a" => '%1A',
  "\x1b" => '%1B',
  "\x1c" => '%1C',
  "\x1d" => '%1D',
  "\x1e" => '%1E',
  "\x1f" => '%1F',
  ' ' => '%20',
  '!' => '%21',
  '"' => '%22',
  '#' => '%23',
  '$' => '%24',
  '%' => '%25',
  '&' => '%26',
  '\'' => '%27',
  '(' => '%28',
  ')' => '%29',
  '*' => '%2A',
  '+' => '%2B',
  ',' => '%2C',
  '/' => '%2F',
  ':' => '%3A',
  ';' => '%3B',
  '<' => '%3C',
  '=' => '%3D',
  '>' => '%3E',
  '?' => '%3F',
  '@' => '%40',
  '[' => '%5B',
  '\\' => '%5C',
  ']' => '%5D',
  '^' => '%5E',
  '`' => '%60',
  '{' => '%7B',
  '|' => '%7C',
  '}' => '%7D',
  "\x7f" => '%7F',
  "\x85" => '%C2%85',
  "\xa0" => '%C2%A0',
  "\xe2\x80\xa8" => '%E2%80%A8',
  "\xe2\x80\xa9" => '%E2%80%A9',
  "\xef\xbc\x81" => '%EF%BC%81',
  "\xef\xbc\x83" => '%EF%BC%83',
  "\xef\xbc\x84" => '%EF%BC%84',
  "\xef\xbc\x86" => '%EF%BC%86',
  "\xef\xbc\x87" => '%EF%BC%87',
  "\xef\xbc\x88" => '%EF%BC%88',
  "\xef\xbc\x89" => '%EF%BC%89',
  "\xef\xbc\x8a" => '%EF%BC%8A',
  "\xef\xbc\x8b" => '%EF%BC%8B',
  "\xef\xbc\x8c" => '%EF%BC%8C',
  "\xef\xbc\x8f" => '%EF%BC%8F',
  "\xef\xbc\x9a" => '%EF%BC%9A',
  "\xef\xbc\x9b" => '%EF%BC%9B',
  "\xef\xbc\x9d" => '%EF%BC%9D',
  "\xef\xbc\x9f" => '%EF%BC%9F',
  "\xef\xbc\xa0" => '%EF%BC%A0',
  "\xef\xbc\xbb" => '%EF%BC%BB',
  "\xef\xbc\xbd" => '%EF%BC%BD'
];

private static function _REPLACER_FOR_ESCAPE_URI__AND__NORMALIZE_URI__AND__FILTER_NORMALIZE_URI($match) {
  $ch = $match[0];
  $map = self::$_ESCAPE_MAP_FOR_ESCAPE_URI__AND__NORMALIZE_URI__AND__FILTER_NORMALIZE_URI;
  return isset($map[$ch]) ? $map[$ch] : '';
}

const _MATCHER_FOR_ESCAPE_HTML = '~[\x00\x22\x26\x27\x3c\x3e]~u';

const _MATCHER_FOR_NORMALIZE_HTML = '~[\x00\x22\x27\x3c\x3e]~u';

const _MATCHER_FOR_ESCAPE_HTML_NOSPACE = '~[\x00\x09-\x0d \x22\x26\x27\x2d\/\x3c-\x3e`\x85\xa0\x{2028}\x{2029}]~u';

const _MATCHER_FOR_NORMALIZE_HTML_NOSPACE = '~[\x00\x09-\x0d \x22\x27\x2d\/\x3c-\x3e`\x85\xa0\x{2028}\x{2029}]~u';

const _MATCHER_FOR_ESCAPE_JS_STRING = '~[\x00\x08-\x0d\x22\x26\x27\/\x3c-\x3e\\\\\x85\x{2028}\x{2029}]~u';

const _MATCHER_FOR_ESCAPE_JS_REGEX = '~[\x00\x08-\x0d\x22\x24\x26-\/\x3a\x3c-\x3f\x5b-\x5e\x7b-\x7d\x85\x{2028}\x{2029}]~u';

const _MATCHER_FOR_ESCAPE_CSS_STRING = '~[\x00\x08-\x0d\x22\x26-\x2a\/\x3a-\x3e@\\\\\x7b\x7d\x85\xa0\x{2028}\x{2029}]~u';

const _MATCHER_FOR_ESCAPE_URI = '~[\x00-\x2c\/\x3a-@\x5b-\x5e`\x7b-\x7d\x7f]~u';

const _MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI = '~[\x00- \x22\x27-\x29\x3c\x3e\\\\\x7b\x7d\x7f\x85\xa0\x{2028}\x{2029}\x{ff01}\x{ff03}\x{ff04}\x{ff06}-\x{ff0c}\x{ff0f}\x{ff1a}\x{ff1b}\x{ff1d}\x{ff1f}\x{ff20}\x{ff3b}\x{ff3d}]~u';

const _FILTER_FOR_FILTER_CSS_VALUE = '~^(?!-*(?:expression|(?:moz-)?binding))(?:[.#]?-?(?:[_a-z0-9-]+)(?:-[_a-z0-9-]+)*-?|-?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[a-z]{1,2}|%)?|!important|)\Z~ui';

const _FILTER_FOR_FILTER_NORMALIZE_URI = '~^(?![^#?]*/(?:\.|%2E){2}(?:[/?#]|\Z))(?:(?:https?|mailto):|[^&:/?#]*(?:[/?#]|\Z))~ui';

const _FILTER_FOR_FILTER_IMAGE_DATA_URI = '~^data:image/(?:bmp|gif|jpe?g|png|tiff|webp);base64,[a-z0-9+/]+=*\Z~ui';

const _FILTER_FOR_FILTER_HTML_ATTRIBUTES = '~^(?!style|on|action|archive|background|cite|classid|codebase|data|dsync|href|longdesc|src|usemap)(?:[a-z0-9_$:-]*)\Z~ui';

const _FILTER_FOR_FILTER_HTML_ELEMENT_NAME = '~^(?!script|style|title|textarea|xmp|no)[a-z0-9_$:-]*\Z~ui';

public static function escapeHtmlHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_ESCAPE_HTML, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE', $value);
}

public static function normalizeHtmlHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_NORMALIZE_HTML, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE', $value);
}

public static function escapeHtmlNospaceHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_ESCAPE_HTML_NOSPACE, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE', $value);
}

public static function normalizeHtmlNospaceHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_NORMALIZE_HTML_NOSPACE, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_HTML__AND__NORMALIZE_HTML__AND__ESCAPE_HTML_NOSPACE__AND__NORMALIZE_HTML_NOSPACE', $value);
}

public static function escapeJsStringHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_ESCAPE_JS_STRING, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX', $value);
}

public static function escapeJsRegexHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_ESCAPE_JS_REGEX, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_JS_STRING__AND__ESCAPE_JS_REGEX', $value);
}

public static function escapeCssStringHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_ESCAPE_CSS_STRING, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_CSS_STRING', $value);
}

public static function filterCssValueHelper($value) {
  $value = (string)$value;
  if (!preg_match(self::_FILTER_FOR_FILTER_CSS_VALUE, $value)) {
    return 'zSoyz';
  }
  return $value;
}

public static function escapeUriHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_ESCAPE_URI, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_URI__AND__NORMALIZE_URI__AND__FILTER_NORMALIZE_URI', $value);
}

public static function normalizeUriHelper($value) {
  $value = (string)$value;
  return preg_replace_callback(self::_MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_URI__AND__NORMALIZE_URI__AND__FILTER_NORMALIZE_URI', $value);
}

public static function filterNormalizeUriHelper($value) {
  $value = (string)$value;
  if (!preg_match(self::_FILTER_FOR_FILTER_NORMALIZE_URI, $value)) {
    return '#zSoyz';
  }
  return preg_replace_callback(self::_MATCHER_FOR_NORMALIZE_URI__AND__FILTER_NORMALIZE_URI, 
      'Goog\Soy\GeneratedSanitize::_REPLACER_FOR_ESCAPE_URI__AND__NORMALIZE_URI__AND__FILTER_NORMALIZE_URI', $value);
}

public static function filterImageDataUriHelper($value) {
  $value = (string)$value;
  if (!preg_match(self::_FILTER_FOR_FILTER_IMAGE_DATA_URI, $value)) {
    return 'data:image/gif;base64,zSoyz';
  }
  return $value;
}

public static function filterHtmlAttributesHelper($value) {
  $value = (string)$value;
  if (!preg_match(self::_FILTER_FOR_FILTER_HTML_ATTRIBUTES, $value)) {
    return 'zSoyz';
  }
  return $value;
}

public static function filterHtmlElementNameHelper($value) {
  $value = (string)$value;
  if (!preg_match(self::_FILTER_FOR_FILTER_HTML_ELEMENT_NAME, $value)) {
    return 'zSoyz';
  }
  return $value;
}
const _HTML_TAG_REGEX = '~<(?:!|/?([a-zA-Z][a-zA-Z0-9:\-]*))(?:[^>\'"]|"[^"]*"|\'[^\']*\')*>~u';

const _LT_REGEX = '~<~';

public static $_SAFE_TAG_WHITELIST = ['b', 'br', 'em', 'i', 's', 'sub', 'sup', 'u'];

}
// END GENERATED CODE
