<?php

namespace Goog\Soy;

class UnsanitizedText extends SanitizedContent
{
	protected $contentKind = ContentKind::TEXT;

	function __construct($content = null, $contentDir = null)
	{
		parent::__construct((string)$content, $contentDir);
	}
}