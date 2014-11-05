<#include "header.ftl">

	<div class="row-fluid marketing">
		<div class="span12">
			<#if date_year ??>
				Entries written on <span class="period year">${date_year}</span>
				<#if date_month ??>
				<span class="period month">${date_month}</span>
					<#if date_day ??>
					<span class="period day">${date_day}</span>
					</#if>
				</#if>
			</#if>
			<ul>
			<#list published_subintervals?keys as intervalLink>
				<#assign interval=published_subintervals[intervalLink]>
				<li><a href="${intervalLink}">
				<!--
				<#if date_year ??>
					we have a year
					<#if date_month ??>
						and a month
						<#if date_day ??>
							and a day -->
							${interval?string("HH")}h
						<#else>
							and NO day -->
							${interval?string("EEE dd")}
						</#if>
					<#else>
						and NO month -->
						${interval?string("MMMMM")}
					</#if>
				<#else>
					we don't even have a year -->
					${interval?string("yyyy")}
				</#if>
				</a></li>
			</#list>
			</ul>
			<#if date_year ??>
			<ul>
			<#list published_during as post>
				<li><h4>${post.date?string("dd MMMM")} - <a href="${post.uri}">${post.title}</a></h4></li>
			</#list>
			</ul>
			</#if>
		</div>
	</div>

	<hr>

<#include "footer.ftl">