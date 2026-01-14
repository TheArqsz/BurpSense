import * as vscode from 'vscode';

/**
 * Registry for managing VS Code commands.
 * Prevents duplicate registrations and simplifies cleanup.
 */
export type CommandHandler = (...args: any[]) => Promise<void> | void;

/**
 * Command definition
 */
export interface CommandDefinition {
    id: string;
    handler: CommandHandler;
}

/**
 * Registry for managing VS Code commands
 * Makes it easy to add, organize, and maintain commands
 */
export class CommandRegistry {
    private commands: Map<string, CommandHandler> = new Map();
    private disposables: vscode.Disposable[] = [];

    /**
     * Registers a single command.
     * 
     * @param id - Command identifier
     * @param handler - Function to execute
     * @throws Error if command already registered
     */
    public register(id: string, handler: CommandHandler): void {
        if (this.commands.has(id)) {
            throw new Error(`Command ${id} is already registered`);
        }

        this.commands.set(id, handler);
        const disposable = vscode.commands.registerCommand(id, handler);
        this.disposables.push(disposable);
    }

    /**
     * Registers multiple commands at once.
     * 
     * @param definitions - Array of command definitions
     */
    public registerMultiple(definitions: CommandDefinition[]): void {
        definitions.forEach(def => this.register(def.id, def.handler));
    }

    /**
     * Gets disposables for extension context.
     * 
     * @returns Array of disposables to add to subscriptions
     */
    public getDisposables(): vscode.Disposable[] {
        return this.disposables;
    }

    /**
     * Checks if a command is registered
     */
    public has(id: string): boolean {
        return this.commands.has(id);
    }

    /**
     * Gets the number of registered commands
     */
    public get count(): number {
        return this.commands.size;
    }

    /**
     * Disposes all registered commands
     */
    public dispose(): void {
        this.disposables.forEach(d => d.dispose());
        this.disposables = [];
        this.commands.clear();
    }
}

/**
 * Groups commands by category for better organization
 */
export class CommandGroup {
    private commands: CommandDefinition[] = [];

    constructor(private readonly name: string) { }

    /**
     * Adds a command to this group.
     * 
     * @param id - Command ID
     * @param handler - Handler function
     * @returns This group for chaining
     */
    public add(id: string, handler: CommandHandler): this {
        this.commands.push({ id, handler });
        return this;
    }

    /**
     * Gets all commands in this group
     */
    public getCommands(): CommandDefinition[] {
        return this.commands;
    }

    /**
     * Gets the group name
     */
    public getName(): string {
        return this.name;
    }

    /**
     * Gets the number of commands in this group
     */
    public get count(): number {
        return this.commands.length;
    }
}

/**
 * Builder for creating command groups fluently
 */
export class CommandGroupBuilder {
    private groups: CommandGroup[] = [];

    /**
     * Creates a new command group
     */
    public group(name: string): CommandGroup {
        const group = new CommandGroup(name);
        this.groups.push(group);
        return group;
    }

    /**
     * Registers all groups to the registry
     */
    public registerAll(registry: CommandRegistry): void {
        this.groups.forEach(group => {
            registry.registerMultiple(group.getCommands());
        });
    }

    /**
     * Gets all groups
     */
    public getGroups(): CommandGroup[] {
        return this.groups;
    }

    /**
     * Gets total command count across all groups
     */
    public get totalCommands(): number {
        return this.groups.reduce((sum, group) => sum + group.count, 0);
    }
}