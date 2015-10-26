<?php

namespace Goog\Soy;

abstract class SanitizedContent
{

	/**
	 * @var int
	 */
	protected $contentKind;

	/**
	 * @var string
	 */
	protected $content;

	/**
	 * @var int
	 */
	protected $contentDir;



	/**
	 * @param string $content
	 * @param int $contentDir
	 */
	function __construct($content = null, $contentDir = null)
	{
		$this->content = $content;
		$this->contentDir = $contentDir;
	}



	/**
	 * @return string
	 */
	function __toString()
	{
		return (string)$this->content;
	}



	/**
	 * @return int
	 */
	public function getContentKind()
	{
		return $this->contentKind;
	}



	/**
	 * @return string
	 */
	public function getContent()
	{
		return $this->content;
	}



	/**
	 * @return int
	 */
	public function getContentDir()
	{
		return $this->contentDir;
	}


}