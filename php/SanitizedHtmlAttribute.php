<?php

namespace Goog\Soy;

class SanitizedHtmlAttribute extends SanitizedContent
{
	protected $contentKind = ContentKind::ATTRIBUTES;

	function __construct($content = null)
	{
		parent::__construct($content, Dir::LTR);
	}
}