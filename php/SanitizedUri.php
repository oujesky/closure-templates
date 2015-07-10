<?php

namespace Goog\Soy;

class SanitizedUri extends SanitizedContent
{
	protected $contentKind = ContentKind::URI;

	function __construct($content = null)
	{
		parent::__construct($content, Dir::LTR);
	}
} 