<?php

namespace Goog\Soy;

class SanitizedCss extends SanitizedContent
{
	protected $contentKind = ContentKind::CSS;

	function __construct($content = null)
	{
		parent::__construct($content, Dir::LTR);
	}
}