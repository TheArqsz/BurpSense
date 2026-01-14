import { LIMITS, SMART_SUGGESTIONS } from '../constants';
import { BurpIssue } from '../types';

/**
 * Suggests relevant issues based on code context.
 * Matches code patterns (SQL keywords, file operations, etc.)
 * to likely security issues.
 */
export class SmartSuggestionService {

    /**
     * Returns issues likely relevant to the given code line.
     * Matches keywords in code against issue patterns.
     * 
     * @param issues - All available issues
     * @param lineTextLower - Lowercased line of code
     * @returns Up to LIMITS.MAX_SUGGESTED_ISSUES matches
     * 
     * @example
     * getSuggestions(issues, "select * from users")
     * // Returns SQL injection issues
     */
    public getSuggestions(issues: BurpIssue[], lineTextLower: string): BurpIssue[] {
        const suggestions = new Set<string>();
        const resultMap = new Map<string, BurpIssue>();

        for (const issue of issues) {
            const issueNameLower = issue.name.toLowerCase();

            for (const mapping of SMART_SUGGESTIONS) {
                const hasKeyword = mapping.keywords.some(kw => lineTextLower.includes(kw));
                const matchesPattern = mapping.issuePatterns.some(pattern =>
                    issueNameLower.includes(pattern)
                );

                if (hasKeyword && matchesPattern && !suggestions.has(issue.id)) {
                    suggestions.add(issue.id);
                    resultMap.set(issue.id, issue);
                    break;
                }
            }
        }

        return Array.from(resultMap.values()).slice(0, LIMITS.MAX_SUGGESTED_ISSUES);
    }
}