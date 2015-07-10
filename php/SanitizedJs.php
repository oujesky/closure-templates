<?php

namespace Goog\Soy;

class SanitizedJs extends SanitizedContent
{
	protected $contentKind = ContentKind::JS;

	function __construct($content = null)
	{
		parent::__construct($content, Dir::LTR);
	}
}