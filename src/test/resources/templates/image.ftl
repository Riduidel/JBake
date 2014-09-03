<#include "header.ftl">
      
      <div class="row-fluid marketing">
		<div class="span12">
			<a href="${content.imageName}"><img src="${content.thumbnailName}"/></a>
			<dl>
			<#list content?keys as key>
				<dt>${key}</dt>
				<#assign value=content[key]>
				<#if value?is_date>
				<dt>${content[key]?datetime}</dt>
				<#else>
				<dt>${content[key]}</dt>
				</#if>
			</#list>
			</dl>
		</div>
	</div>

	<hr>
	
<#include "footer.ftl">
